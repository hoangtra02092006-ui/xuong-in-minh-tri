package vn.minhtri.xuongin.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.minhtri.xuongin.storage.FileStore;
import vn.minhtri.xuongin.storage.RetentionJanitor;

/** POST /api/v1/files and GET /api/v1/files/{id}/preview against the real Spring context (spec 6.1, 6.2, 6.6). */
@SpringBootTest
@AutoConfigureMockMvc
class FileControllerTest {

    /** 3 MB sample JPG is above this, every other sample below. */
    private static final long MAX_BYTES = 2_000_000;

    @TempDir
    static Path storage;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.storage.path", storage::toString);
        registry.add("app.storage.max-file-size", () -> MAX_BYTES);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FileStore store;

    @Autowired
    RetentionJanitor janitor;

    @Test
    void uploadPdfReturnsSpecShapeWithTrimmedSize() throws Exception {
        mvc.perform(multipart("/api/v1/files").file(sample("thu-hinh-nho-giua-A4.pdf", "application/pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$[0].originalName").value("thu-hinh-nho-giua-A4.pdf"))
                .andExpect(jsonPath("$[0].widthMm").value(30.0))
                .andExpect(jsonPath("$[0].heightMm").value(30.0))
                .andExpect(jsonPath("$[0].sourceWidthMm").value(210.0))
                .andExpect(jsonPath("$[0].sourceHeightMm").value(297.0))
                .andExpect(jsonPath("$[0].trimmed").value(true))
                .andExpect(jsonPath("$[0].type").value("PDF"))
                .andExpect(jsonPath("$[0].pageCount").value(1))
                .andExpect(jsonPath("$[0].previewUrl", matchesPattern("/api/v1/files/[0-9a-f-]{36}/preview")));
    }

    @Test
    void uploadPngRoundsToTwoDecimals() throws Exception {
        String body = mvc.perform(multipart("/api/v1/files").file(sample("anh-600x400-300dpi.png", "image/png")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].widthMm").value(50.8))
                .andExpect(jsonPath("$[0].heightMm").value(33.87))
                .andExpect(jsonPath("$[0].trimmed").value(false))
                .andExpect(jsonPath("$[0].type").value("IMAGE"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$[0].id");
        StoredFile stored = store.require(id);
        assertThat(stored.hasAlpha()).isFalse();
        assertThat(stored.occupancy()).isNull();
        assertThat(Files.exists(store.pathOf(stored))).isTrue();
        assertThat(store.pathOf(stored).getFileName().toString()).isEqualTo(id + ".png");
    }

    @Test
    void severalFilesInOneRequest() throws Exception {
        mvc.perform(multipart("/api/v1/files")
                        .file(sample("nhan-7x3.pdf", "application/pdf"))
                        .file(sample("nhan-5x7.pdf", "application/pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].widthMm").value(50.0))
                .andExpect(jsonPath("$[1].heightMm").value(70.0));
    }

    @Test
    void wrongExtensionIs415() throws Exception {
        MockMultipartFile doc = new MockMultipartFile("files", "bang-gia.docx", "application/octet-stream", new byte[] {1, 2});

        mvc.perform(multipart("/api/v1/files").file(doc))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FORMAT"))
                .andExpect(jsonPath("$.error.message", containsString("bang-gia.docx")))
                .andExpect(jsonPath("$.error.details.fileName").value("bang-gia.docx"));
    }

    @Test
    void tooLargeIs413() throws Exception {
        mvc.perform(multipart("/api/v1/files").file(sample("thu-anh-lon-3mb.jpg", "image/jpeg")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.error.details.maxBytes").value(MAX_BYTES));
    }

    @Test
    void unreadablePdfIs422AndLeavesNoFileOnDisk() throws Exception {
        long before = countUploads();
        MockMultipartFile broken = new MockMultipartFile("files", "hong.pdf", "application/pdf", "not a pdf".getBytes());

        mvc.perform(multipart("/api/v1/files").file(broken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SIZE_UNREADABLE"))
                .andExpect(jsonPath("$.error.message", containsString("nhập kích thước bằng tay")));

        assertThat(countUploads()).isEqualTo(before);
    }

    @Test
    void noFileIs400() throws Exception {
        mvc.perform(multipart("/api/v1/files"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("Chưa chọn file nào để tải lên."));
    }

    @Test
    void nonMultipartRequestIs400() throws Exception {
        mvc.perform(post("/api/v1/files").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void pdfPreviewIsPngAtMost360px() throws Exception {
        String id = upload("nhan-13x4.pdf", "application/pdf");

        byte[] png = mvc.perform(get("/api/v1/files/{id}/preview", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", containsString("max-age")))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isLessThanOrEqualTo(360);
        assertThat((double) img.getWidth() / img.getHeight()).isCloseTo(130.0 / 40.0, org.assertj.core.data.Offset.offset(0.05));
        // Second call is served from the cache.
        assertThat(store.findPreview(id)).isPresent();
        mvc.perform(get("/api/v1/files/{id}/preview", id)).andExpect(status().isOk());
    }

    @Test
    void imagePreviewIsJpeg() throws Exception {
        String id = upload("anh-600x400-300dpi.png", "image/png");

        mvc.perform(get("/api/v1/files/{id}/preview", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void unknownPreviewIs404WithErrorBody() throws Exception {
        mvc.perform(get("/api/v1/files/{id}/preview", "khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.details.fileId").value("khong-co"));
    }

    @Test
    void previewOfAFileGoneFromDiskIsAnErrorNotACrash() throws Exception {
        String id = upload("nhan-7x3.pdf", "application/pdf");
        Files.delete(store.pathOf(store.require(id)));

        mvc.perform(get("/api/v1/files/{id}/preview", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void janitorBeanPurgesUploadsPastRetention() throws Exception {
        String id = upload("nhan-7x3.pdf", "application/pdf");
        StoredFile file = store.require(id);
        // Pretend it was uploaded 49 hours ago.
        store.save(new StoredFile(file.id(), file.originalName(), file.storedPath(), file.type(), file.widthMm(), file.heightMm(),
                file.sourceWidthMm(), file.sourceHeightMm(), file.contentBox(), file.occupancy(), file.pageRotation(),
                file.pageCount(), file.hasAlpha(), file.sizeBytes(), Instant.now().minusSeconds(49 * 3600)));

        janitor.sweep();

        assertThat(store.find(id)).isEmpty();
        assertThat(Files.exists(store.pathOf(file))).isFalse();
        mvc.perform(get("/api/v1/files/{id}/preview", id)).andExpect(status().isNotFound());
    }

    private String upload(String name, String type) throws Exception {
        String body = mvc.perform(multipart("/api/v1/files").file(sample(name, type)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private static MockMultipartFile sample(String name, String type) throws IOException {
        return new MockMultipartFile("files", name, type, Files.readAllBytes(TestFiles.sample(name)));
    }

    private static long countUploads() throws IOException {
        Path dir = storage.resolve("uploads");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var files = Files.list(dir)) {
            return files.count();
        }
    }
}
