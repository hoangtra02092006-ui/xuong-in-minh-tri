package vn.minhtri.xuongin.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.job.InMemoryJobStore;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.nesting.model.NestItemRequest;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.storage.FileStore;

/** File names, format parsing and the zip's error files (spec 6.5) with a fake PDF composer. */
class ExportServiceTest {

    @TempDir
    Path dir;

    static final String JOB_ID = "a7e3f019-1111-2222-3333-444455556666";

    private static Job job(int sheets) {
        NestRequest request = new NestRequest(null, 570, 5, 3, null, true, true,
                List.of(new NestItemRequest("f", 1, true, null, null)));
        List<Sheet> list = new java.util.ArrayList<>();
        for (int i = 0; i < sheets; i++) {
            list.add(new Sheet(i, 570, 1004.9, 0.5, List.of()));
        }
        return Job.pending(JOB_ID, request).done(new NestResult(list, null));
    }

    @SuppressWarnings("unchecked")
    private ExportService service(PdfComposer composer, CutFileWriter cut) {
        ExportStore store = new ExportStore(dir, new InMemoryJobStore(), mock(FileStore.class), composer);
        ObjectProvider<CutFileWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cut);
        when(provider.getObject()).thenReturn(cut);
        return new ExportService(store, mock(TifPrebuildService.class), provider, new ExportProperties(300, 1000));
    }

    @Test
    void fileNamesFollowTheSpec() {
        Sheet sheet = new Sheet(0, 570, 1004.9, 0.5, List.of());
        assertThat(ExportFormat.PDF.fileName(JOB_ID, sheet)).isEqualTo("minh-tri-a7e3f019-tam01-57x100cm.pdf");
        assertThat(ExportFormat.TIF.fileName(JOB_ID, sheet)).isEqualTo("minh-tri-a7e3f019-tam01-57x100cm.tif");
        assertThat(ExportFormat.CUT.fileName(JOB_ID, sheet)).isEqualTo("minh-tri-a7e3f019-tam01-57x100cm-cat.pdf");
        assertThat(ExportFormat.CUT.zipName(JOB_ID)).isEqualTo("minh-tri-a7e3f019-cut.zip");
        assertThat(ExportFormat.sheetTag(11)).isEqualTo("tam12");
    }

    @Test
    void parseAcceptsOnlyThreeFormats() {
        assertThat(ExportFormat.parse("PDF")).isEqualTo(ExportFormat.PDF);
        assertThat(ExportFormat.parse(" tif ")).isEqualTo(ExportFormat.TIF);
        assertThatThrownBy(() -> ExportFormat.parse(null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> ExportFormat.parse("svg"))
                .hasMessage("Định dạng \"svg\" không hỗ trợ. Chỉ nhận pdf, tif hoặc cut.");
    }

    @Test
    void zipWritesErrorFileForAFailedSheetAndGoesOn() throws Exception {
        PdfComposer composer = mock(PdfComposer.class);
        doAnswer(inv -> {
            Sheet sheet = inv.getArgument(0);
            if (sheet.index() == 1) {
                throw new ApiException(ErrorCode.FILE_NOT_FOUND, "Không tìm thấy file nguồn.");
            }
            if (sheet.index() == 2) {
                throw new IllegalStateException("hỏng");
            }
            OutputStream out = inv.getArgument(4);
            out.write(("pdf-" + sheet.index()).getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(composer).compose(any(), any(), any(), anyBoolean(), any());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service(composer, null).zip(job(4), ExportFormat.PDF, out);
        Map<String, String> entries = unzip(out.toByteArray());
        assertThat(entries.keySet()).containsExactly("minh-tri-a7e3f019-tam01-57x100cm.pdf", "LOI-tam02.txt",
                "LOI-tam03.txt", "minh-tri-a7e3f019-tam04-57x100cm.pdf");
        assertThat(entries.get("LOI-tam02.txt")).contains("Tấm 2").contains("FILE_NOT_FOUND");
        assertThat(entries.get("LOI-tam03.txt")).contains("hỏng");
        assertThat(entries.get("minh-tri-a7e3f019-tam04-57x100cm.pdf")).isEqualTo("pdf-3");
    }

    @Test
    void cutWithoutWriterIsInternalError() {
        ExportService service = service(mock(PdfComposer.class), null);
        assertThatThrownBy(() -> service.checkAvailable(ExportFormat.CUT))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        Job job = job(1);
        assertThatThrownBy(() -> service.file(job, job.result().sheets().get(0), ExportFormat.CUT))
                .hasMessage(ExportService.CUT_UNAVAILABLE);
        service.checkAvailable(ExportFormat.PDF);
    }

    @Test
    void cutIsTracedFromThePdfWithoutFrames() throws Exception {
        PdfComposer composer = mock(PdfComposer.class);
        List<Boolean> frames = new java.util.ArrayList<>();
        doAnswer(inv -> {
            frames.add(inv.getArgument(3));
            return null;
        }).when(composer).compose(any(), any(), any(), anyBoolean(), any());
        CutFileWriter cut = (sheet, pdf, out) -> out.write(pdf.getFileName().toString().getBytes(StandardCharsets.UTF_8));
        Job job = job(1);
        Path file = service(composer, cut).file(job, job.result().sheets().get(0), ExportFormat.CUT);
        assertThat(file.getFileName().toString()).isEqualTo("tam-01-cat.pdf");
        assertThat(java.nio.file.Files.readString(file)).isEqualTo("tam-01.pdf");
        assertThat(frames).containsExactly(false);

        Path print = service(composer, cut).file(job, job.result().sheets().get(0), ExportFormat.PDF);
        assertThat(print.getFileName().toString()).isEqualTo("tam-01-vien.pdf");
        assertThat(frames).containsExactly(false, true);
    }

    @Test
    void ioFailureBecomesInternalError() throws Exception {
        PdfComposer composer = mock(PdfComposer.class);
        doAnswer(inv -> {
            throw new IOException("đĩa đầy");
        }).when(composer).compose(any(), any(), any(), anyBoolean(), any());
        Job job = job(1);
        assertThatThrownBy(() -> service(composer, null).file(job, job.result().sheets().get(0), ExportFormat.PDF))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(e.getMessage()).contains("đĩa đầy");
                });
    }

    static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
