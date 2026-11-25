package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.RasterMemoryGuard;
import vn.minhtri.xuongin.raster.RasterProperties;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * Per-sheet files and the streamed "download all" zip (spec 6.5). Every file is produced on disk
 * first (PDF and cut file on demand, TIF by the background builder), then copied to the
 * response, so a failure never leaves half a file in the zip: a failed sheet becomes
 * {@code LOI-tamNN.txt} and the zip goes on.
 */
@Service
public class ExportService {

    static final String CUT_UNAVAILABLE = "Máy chủ chưa có bộ dựng file cắt. Hãy báo người quản lý.";
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ExportStore store;
    private final TifPrebuildService tifs;
    private final ObjectProvider<CutFileWriter> cutWriter;
    private final ExportProperties properties;
    private final RasterMemoryGuard guard;
    private final SourceImageCost sourceImages;
    private final long budgetWaitMs;

    public ExportService(ExportStore store, TifPrebuildService tifs, ObjectProvider<CutFileWriter> cutWriter,
            ExportProperties properties, RasterMemoryGuard guard, SourceImageCost sourceImages,
            RasterProperties rasterProperties) {
        this.store = store;
        this.tifs = tifs;
        this.cutWriter = cutWriter;
        this.properties = properties;
        this.guard = guard;
        this.sourceImages = sourceImages;
        this.budgetWaitMs = rasterProperties.budgetWaitMs();
    }

    /** The sheet's file on disk, built if needed. {@code job} must be DONE and {@code sheet} one of its sheets. */
    public Path file(Job job, Sheet sheet, ExportFormat format) {
        try {
            return switch (format) {
                case PDF -> pdf(job, sheet);
                case TIF -> tifs.await(job, sheet.index(), properties.waitTimeoutMs());
                case CUT -> cut(job, sheet);
            };
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không dựng được file " + format.key() + " tấm "
                    + (sheet.index() + 1) + ": " + e.getMessage());
        }
    }

    /** Fails fast (before a zip starts streaming) when the format cannot be built on this server. */
    public void checkAvailable(ExportFormat format) {
        if (format == ExportFormat.CUT && cutWriter.getIfAvailable() == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, CUT_UNAVAILABLE);
        }
    }

    /** Composing from images decodes them, so a sheet with images reserves its share of the raster budget. */
    private Path pdf(Job job, Sheet sheet) throws IOException {
        Path existing = store.printPdfPath(job, sheet);
        if (Files.exists(existing)) {
            return existing;
        }
        long need = sourceImages.of(sheet);
        if (need == 0) {
            return store.printPdf(job, sheet);
        }
        try (RasterMemoryGuard.Reservation ignored = guard.reserve(need, budgetWaitMs, () -> busy(sheet, "PDF"))) {
            return store.printPdf(job, sheet);
        } catch (OutOfMemoryError e) {
            log.warn("PDF of job {} sheet {} ran out of memory", job.jobId(), sheet.index() + 1);
            throw RasterMemoryGuard.outOfMemory(sheet.index(), "PDF");
        }
    }

    /**
     * Built on the request thread, so it shares the raster budget with the background TIF worker
     * (spec 10.6): reserve the estimate, wait in line if needed, then compose and rasterize.
     */
    private Path cut(Job job, Sheet sheet) throws IOException {
        checkAvailable(ExportFormat.CUT);
        CutFileWriter writer = cutWriter.getObject();
        Path target = store.sheetFile(job.jobId(), sheet.index(), "-cat.pdf");
        if (Files.exists(target)) {
            return target;
        }
        long need = writer.estimateBytes(sheet) + sourceImages.of(sheet);
        try (RasterMemoryGuard.Reservation ignored = guard.reserve(need, budgetWaitMs, () -> busy(sheet, "cắt"))) {
            Path source = store.pdfWithoutCutLines(job, sheet);
            return store.ensure(target, out -> writer.write(sheet, source, out));
        } catch (OutOfMemoryError e) {
            log.warn("Cut file of job {} sheet {} ran out of memory", job.jobId(), sheet.index() + 1);
            throw RasterMemoryGuard.outOfMemory(sheet.index(), "cắt");
        }
    }

    private static ApiException busy(Sheet sheet, String kind) {
        return new ApiException(ErrorCode.JOB_NOT_READY, "Máy chủ đang bận dựng file khác nên chưa dựng được file "
                + kind + " tấm " + (sheet.index() + 1) + ". Hãy thử lại sau ít phút.",
                Map.of("sheetIndex", sheet.index()));
    }

    /**
     * Streams the zip of every sheet in order, one sheet in memory at a time, flushing after each.
     * For TIF the ready sheets are taken as they are and the others are built in order.
     */
    public void zip(Job job, ExportFormat format, OutputStream out) throws IOException {
        long started = System.nanoTime();
        int failed = 0;
        ZipOutputStream zip = new ZipOutputStream(out);
        for (Sheet sheet : job.result().sheets()) {
            Path file;
            try {
                file = file(job, sheet, format);
            } catch (RuntimeException e) {
                failed++;
                writeError(zip, sheet, e);
                continue;
            }
            zip.putNextEntry(new ZipEntry(format.fileName(job.jobId(), sheet)));
            Files.copy(file, zip);
            zip.closeEntry();
            zip.flush();
        }
        zip.finish();
        zip.flush();
        log.info("Zip {} of job {}: {} sheets, {} failed, {} ms", format.key(), job.jobId(),
                job.result().sheets().size(), failed, (System.nanoTime() - started) / 1_000_000);
    }

    private static void writeError(ZipOutputStream zip, Sheet sheet, RuntimeException e) throws IOException {
        String reason = e instanceof ApiException api ? api.code() + ": " + api.getMessage() : String.valueOf(e.getMessage());
        if (!(e instanceof ApiException)) {
            log.error("Sheet {} failed inside a zip", sheet.index() + 1, e);
        }
        zip.putNextEntry(new ZipEntry("LOI-" + ExportFormat.sheetTag(sheet.index()) + ".txt"));
        zip.write(("Tấm " + (sheet.index() + 1) + " không dựng được.\r\n" + reason + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.flush();
    }
}
