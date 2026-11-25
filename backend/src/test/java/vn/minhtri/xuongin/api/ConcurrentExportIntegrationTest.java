package vn.minhtri.xuongin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.minhtri.xuongin.export.tif.StandInTifEncoderFactory;
import vn.minhtri.xuongin.export.tif.TifEncoder;
import vn.minhtri.xuongin.export.tif.TifEncoderFactory;
import vn.minhtri.xuongin.raster.RasterMemoryGuard;
import vn.minhtri.xuongin.raster.RasterProperties;
import vn.minhtri.xuongin.shared.ApiException;

/**
 * Review blocker: cut files are built on request threads while the TIF worker builds in the
 * background. With a small "heap" (budget 48 MB) several parallel cut requests during a TIF build
 * must all succeed (or get a proper error), never exceed the shared budget, never INTERNAL_ERROR.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentExportIntegrationTest {

    /** Budget = heap × 0.5 = 48 MB. One cut of a 250 × ~190 mm sheet at 300 DPI reserves ~32 MB. */
    private static final long HEAP = 96L << 20;
    private static final long WAIT_MS = 60_000;

    @TempDir
    static Path storage;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.storage.path", storage::toString);
        registry.add("app.export.tif-dpi", () -> 40);
        registry.add("app.export.wait-timeout-ms", () -> WAIT_MS);
    }

    /** Records the highest total reserved and how many builds ran at once. */
    static class TrackingGuard extends RasterMemoryGuard {
        final AtomicLong peakBytes = new AtomicLong();
        final AtomicLong biggest = new AtomicLong();

        TrackingGuard() {
            super(RasterProperties.defaults(), () -> HEAP);
        }

        @Override
        public Reservation reserve(long bytes, long waitMs, Supplier<ApiException> busy) {
            Reservation reservation = super.reserve(bytes, waitMs, busy);
            peakBytes.accumulateAndGet(reservedBytes(), Math::max);
            biggest.accumulateAndGet(reservation.bytes(), Math::max);
            return reservation;
        }
    }

    /** Holds each TIF build (inside its reservation) long enough for the cut requests to overlap it. */
    static class SlowEncoderFactory extends StandInTifEncoderFactory {
        @Override
        public TifEncoder open(Path target, int width, int height, int dpi, byte[] inkMask) throws IOException {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.open(target, width, height, dpi, inkMask);
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        TrackingGuard trackingGuard() {
            return new TrackingGuard();
        }

        @Bean
        @Primary
        TifEncoderFactory slowEncoderFactory() {
            return new SlowEncoderFactory();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    TrackingGuard guard;

    @Test
    void parallelCutRequestsWhileTifsBuildStayWithinTheSharedBudget() throws Exception {
        String fileId = upload("nhan-20x18.pdf");
        // 250 mm roll, at most 200 mm per sheet: one 20 x 18 cm label per sheet, 3 sheets.
        String jobId = createJob("{\"sheetWidthMm\":250,\"marginMm\":5,\"gapMm\":3,\"maxSheetLengthMm\":200,"
                + "\"allowRotateGlobal\":true,\"drawCutLines\":false,\"items\":[{\"fileId\":\"" + fileId
                + "\",\"quantity\":3,\"allowRotate\":true}]}");
        int sheets = awaitSheets(jobId);
        assertThat(sheets).isEqualTo(3);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<MockHttpServletResponse>> cuts = new ArrayList<>();
            for (int i = 0; i < sheets; i++) {
                int index = i;
                cuts.add(pool.submit(() -> mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/{i}/cut", jobId, index))
                        .andReturn().getResponse()));
            }
            for (Future<MockHttpServletResponse> cut : cuts) {
                MockHttpServletResponse response = cut.get();
                assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
                assertThat(response.getContentAsByteArray()).startsWith("%PDF".getBytes());
            }
        } finally {
            pool.shutdownNow();
        }
        awaitTifsReady(jobId, sheets);

        long allowed = guard.allowedBytes();
        // Cut builds did reserve (more than half the budget each), so no two of them overlapped...
        assertThat(guard.biggest.get()).isGreaterThan(allowed / 2);
        // ...and the total reserved never went over the budget.
        assertThat(guard.peakBytes.get()).isLessThanOrEqualTo(allowed);
        assertThat(guard.reservedBytes()).isZero();
    }

    private String upload(String name) throws Exception {
        Path file = NestingApiIntegrationTest.samples().resolve(name);
        MockMultipartFile part = new MockMultipartFile("files", name, "application/pdf", Files.readAllBytes(file));
        String body = mvc.perform(multipart("/api/v1/files").file(part))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private String createJob(String json) throws Exception {
        String body = mvc.perform(post("/api/v1/nesting/jobs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.jobId");
    }

    private int awaitSheets(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            String body = poll(jobId);
            String state = JsonPath.read(body, "$.status");
            assertThat(state).as(body).isNotEqualTo("FAILED");
            if (state.equals("DONE")) {
                return JsonPath.read(body, "$.result.stats.totalSheets");
            }
            assertThat(System.currentTimeMillis()).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private void awaitTifsReady(String jobId, int sheets) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            List<String> states = JsonPath.read(poll(jobId), "$.result.sheets[*].tif.status");
            assertThat(states).doesNotContain("FAILED");
            if (states.stream().filter("READY"::equals).count() == sheets) {
                return;
            }
            assertThat(System.currentTimeMillis()).as("TIFs " + states).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private String poll(String jobId) throws Exception {
        return mvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
