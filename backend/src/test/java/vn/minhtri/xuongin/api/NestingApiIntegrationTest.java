package vn.minhtri.xuongin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.minhtri.xuongin.export.SheetTifBuilder;
import vn.minhtri.xuongin.export.tif.StandInTifEncoderFactory;
import vn.minhtri.xuongin.export.tif.TifEncoderFactory;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * End to end against the real context and the real engine: upload samples, create a job, poll it,
 * read the TIF states, download each format and the zips (spec 6.3 to 6.6, design 4a). TIFs use
 * the stand-in colour converter so this runs without the Adobe ICC profile (CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
class NestingApiIntegrationTest {

    private static final double PT_PER_MM = 72.0 / 25.4;
    private static final long WAIT_MS = 60_000;

    @TempDir
    static Path storage;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.storage.path", storage::toString);
        // Low DPI keeps the TIF builds fast; the encoder path is the same at 300.
        registry.add("app.export.tif-dpi", () -> 40);
        registry.add("app.export.wait-timeout-ms", () -> WAIT_MS);
    }

    @TestConfiguration
    static class StandInEncoder {
        @Bean
        @Primary
        TifEncoderFactory standInTifEncoderFactory() {
            return new StandInTifEncoderFactory();
        }
    }

    @Autowired
    MockMvc mvc;

    @SpyBean
    SheetTifBuilder tifBuilder;

    // ---- full flow ----

    @Test
    void uploadNestPollAndDownloadEveryFormat() throws Exception {
        String a = upload("nhan-7x3.pdf");
        String b = upload("nhan-5x7.pdf");
        // 200 mm roll, sheets of at most 100 mm: 70x30 x6 and 50x70 x4 cannot fit on one sheet.
        String jobId = createJob(body(200, 5, 3, 100.0, item(a, 6), item(b, 4)));

        String body = awaitDone(jobId);
        assertThat((Integer) JsonPath.read(body, "$.progress")).isEqualTo(100);
        assertThat((String) JsonPath.read(body, "$.finishedAt")).isNotBlank();
        assertThat((Integer) JsonPath.read(body, "$.result.stats.totalPieces")).isEqualTo(10);
        assertThat(body).doesNotContain("\"request\"").doesNotContain("\"items\"");
        int sheets = JsonPath.read(body, "$.result.stats.totalSheets");
        assertThat(sheets).isGreaterThanOrEqualTo(2);
        List<String> tifStates = JsonPath.read(body, "$.result.sheets[*].tif.status");
        assertThat(tifStates).hasSize(sheets).allMatch(s -> List.of("QUEUED", "BUILDING", "READY").contains(s));

        String ready = awaitTifs(jobId, sheets);
        List<Integer> sizes = JsonPath.read(ready, "$.result.sheets[*].tif.sizeBytes");
        assertThat(sizes).allMatch(size -> size > 0);
        assertThat(Files.isRegularFile(storage.resolve("exports").resolve(jobId).resolve("tam-01.tif"))).isTrue();

        double widthMm = JsonPath.read(body, "$.result.sheets[0].widthMm");
        double lengthMm = ((Number) JsonPath.read(body, "$.result.sheets[0].lengthMm")).doubleValue();
        byte[] pdf = mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"minh-tri-" + jobId.substring(0, 8)
                                + "-tam01-20x\\d+cm\\.pdf\"")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isCloseTo((float) (widthMm * PT_PER_MM),
                    org.assertj.core.data.Offset.offset(0.01f));
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isCloseTo((float) (lengthMm * PT_PER_MM),
                    org.assertj.core.data.Offset.offset(0.01f));
        }

        byte[] tif = mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/tiff"))
                .andExpect(header().string("Content-Disposition", containsString("-tam01-20x")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(StandInTifEncoderFactory.samplesPerPixel(tif)).isEqualTo(5);

        Map<String, byte[]> pdfZip = zip(jobId, "pdf");
        assertThat(pdfZip).hasSize(sheets);
        assertThat(pdfZip.keySet()).allMatch(n -> n.matches("minh-tri-" + jobId.substring(0, 8) + "-tam\\d\\d-.*\\.pdf"));
        for (byte[] entry : pdfZip.values()) {
            try (PDDocument doc = Loader.loadPDF(entry)) {
                assertThat(doc.getNumberOfPages()).isEqualTo(1);
            }
        }

        Map<String, byte[]> tifZip = zip(jobId, "tif");
        assertThat(tifZip).hasSize(sheets);
        assertThat(tifZip.keySet()).allMatch(n -> n.endsWith(".tif"));
    }

    @Test
    void failedSheetBecomesErrorFileInZipAndFailedState() throws Exception {
        doThrow(new ApiException(ErrorCode.TIFF_TOO_LARGE, "Tấm 2 quá lớn để dựng file TIF."))
                .when(tifBuilder).build(any(), argThat((Sheet s) -> s != null && s.index() == 1), any());
        String a = upload("nhan-7x3.pdf");
        String jobId = createJob(body(100, 5, 3, 50.0, item(a, 3)));
        String body = awaitDone(jobId);
        int sheets = JsonPath.read(body, "$.result.stats.totalSheets");
        assertThat(sheets).isEqualTo(3);

        Map<String, byte[]> tifZip = zip(jobId, "tif");
        assertThat(tifZip.keySet()).hasSize(3).contains("LOI-tam02.txt");
        assertThat(new String(tifZip.get("LOI-tam02.txt"), java.nio.charset.StandardCharsets.UTF_8))
                .contains("TIFF_TOO_LARGE").contains("Tấm 2 quá lớn");
        assertThat(tifZip.keySet().stream().filter(n -> n.endsWith(".tif"))).hasSize(2);

        mvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                .andExpect(jsonPath("$.result.sheets[1].tif.status").value("FAILED"))
                .andExpect(jsonPath("$.result.sheets[1].tif.error.code").value("TIFF_TOO_LARGE"))
                .andExpect(jsonPath("$.result.sheets[0].tif.status").value("READY"));
        mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/1/tif", jobId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TIFF_TOO_LARGE"));
    }

    @Test
    void prioritizeAnswersTheSheetState() throws Exception {
        String a = upload("nhan-5x7.pdf");
        String jobId = createJob(body(200, 5, 3, null, item(a, 2)));
        awaitDone(jobId);
        mvc.perform(post("/api/v1/nesting/jobs/{id}/sheets/0/tif/prioritize", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", matchesPattern("QUEUED|BUILDING|READY")));
        mvc.perform(post("/api/v1/nesting/jobs/{id}/sheets/5/tif/prioritize", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Lần ghép này chỉ có 1 tấm, không có tấm số 6."));
    }

    @Test
    void cutFileHasCatSuffixAndZipHasEverySheet() throws Exception {
        String a = upload("nhan-7x3.pdf");
        String jobId = createJob(body(100, 5, 3, 50.0, item(a, 2)));
        awaitDone(jobId);
        byte[] cut = mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/cut", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", matchesPattern(".*-tam01-10x\\d+cm-cat\\.pdf\"")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(cut)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
        // The cut file is traced from the sheet PDF without the grey frames (drawCutLines=true here).
        Path dir = storage.resolve("exports").resolve(jobId);
        assertThat(dir.resolve("tam-01.pdf")).exists();
        assertThat(dir.resolve("tam-01-cat.pdf")).exists();

        Map<String, byte[]> zip = zip(jobId, "cut");
        assertThat(zip.keySet()).hasSize(2).allMatch(n -> n.endsWith("-cat.pdf"));
    }

    // ---- validation (spec 6.3, 6.6) ----

    @Test
    void createAnswers202WithPendingJob() throws Exception {
        String a = upload("nhan-7x3.pdf");
        mvc.perform(post("/api/v1/nesting/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(570, 5, 3, null, item(a, 1))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void emptyItemsIsInvalidRequest() throws Exception {
        mvc.perform(post("/api/v1/nesting/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(570, 5, 3, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Danh sách hình không được rỗng."))
                .andExpect(jsonPath("$.error.details").isMap());
    }

    @Test
    void unknownFileIsFileNotFound() throws Exception {
        mvc.perform(post("/api/v1/nesting/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(570, 5, 3, null, item("khong-co", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.details.fileId").value("khong-co"));
    }

    @Test
    void pieceWiderThanRollIsRefusedBeforeTheJob() throws Exception {
        String wide = upload("loi-qua-kho-60x59.pdf");
        mvc.perform(post("/api/v1/nesting/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body(570, 5, 3, null, item(wide, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ITEM_WIDER_THAN_SHEET"))
                .andExpect(jsonPath("$.error.details.fileId").value(wide))
                .andExpect(jsonPath("$.error.details.label").value("loi-qua-kho-60x59.pdf"))
                .andExpect(jsonPath("$.error.message").value(
                        "Hình \"loi-qua-kho-60x59.pdf\" 60x59 cm rộng hơn khổ 57 cm (đã tính cả trường hợp xoay)."));
    }

    @Test
    void marginTooLargeEndsAsFailedJobAndDownloadsAreNotReady() throws Exception {
        String a = upload("nhan-7x3.pdf");
        String jobId = createJob(body(100, 50, 3, null, item(a, 1)));
        String body = awaitFinished(jobId);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(body, "$.error.code")).isEqualTo("INVALID_REQUEST");
        assertThat(body).doesNotContain("\"result\"");

        mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_READY"))
                .andExpect(jsonPath("$.error.message").value("Lần ghép này chưa có kết quả (trạng thái: lỗi)."));
    }

    @Test
    void unknownJobAndBadIndexAndBadFormat() throws Exception {
        mvc.perform(get("/api/v1/nesting/jobs/{id}", "khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message")
                        .value("Không tìm thấy lần ghép khong-co. Có thể máy chủ đã khởi động lại."));

        String a = upload("nhan-7x3.pdf");
        String jobId = createJob(body(570, 5, 3, null, item(a, 1)));
        awaitDone(jobId);
        mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/3/pdf", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Lần ghép này chỉ có 1 tấm, không có tấm số 4."));
        mvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId).param("format", "png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Định dạng \"png\" không hỗ trợ. Chỉ nhận pdf, tif hoặc cut."));
        mvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/abc/pdf", jobId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ---- helpers ----

    private String upload(String name) throws Exception {
        Path file = samples().resolve(name);
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

    private String awaitDone(String jobId) throws Exception {
        String body = awaitFinished(jobId);
        assertThat((String) JsonPath.read(body, "$.status")).as(body).isEqualTo("DONE");
        return body;
    }

    private String awaitFinished(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            String body = poll(jobId);
            String state = JsonPath.read(body, "$.status");
            if (state.equals("DONE") || state.equals("FAILED")) {
                return body;
            }
            assertThat(System.currentTimeMillis()).as("job still " + state).isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private String awaitTifs(String jobId, int sheets) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (true) {
            String body = poll(jobId);
            List<String> states = JsonPath.read(body, "$.result.sheets[*].tif.status");
            assertThat(states).doesNotContain("FAILED");
            if (states.stream().filter("READY"::equals).count() == sheets) {
                return body;
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

    private Map<String, byte[]> zip(String jobId, String format) throws Exception {
        MvcResult started = mvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId).param("format", format))
                .andExpect(request().asyncStarted())
                .andReturn();
        byte[] bytes = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"minh-tri-" + jobId.substring(0, 8) + "-" + format + ".zip\""))
                .andReturn().getResponse().getContentAsByteArray();
        return unzip(bytes);
    }

    static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    static Path samples() {
        Path fromBackend = Path.of("..", "samples");
        return Files.isDirectory(fromBackend) ? fromBackend : Path.of("samples");
    }

    private static String item(String fileId, int quantity) {
        return "{\"fileId\":\"" + fileId + "\",\"quantity\":" + quantity + ",\"allowRotate\":true}";
    }

    private static String body(double width, double margin, double gap, Double maxLength, String... items) {
        return "{\"mode\":\"ORTHOGONAL\",\"sheetWidthMm\":" + width + ",\"marginMm\":" + margin + ",\"gapMm\":" + gap
                + ",\"maxSheetLengthMm\":" + maxLength + ",\"allowRotateGlobal\":true,\"drawCutLines\":true,"
                + "\"items\":[" + String.join(",", items) + "]}";
    }
}
