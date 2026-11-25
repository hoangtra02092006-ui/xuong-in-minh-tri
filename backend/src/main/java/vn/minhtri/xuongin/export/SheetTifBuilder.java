package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.export.tif.TifEncoder;
import vn.minhtri.xuongin.export.tif.TifEncoderFactory;
import vn.minhtri.xuongin.export.tif.TifResult;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.InkMask;
import vn.minhtri.xuongin.raster.RasterMemoryGuard;
import vn.minhtri.xuongin.raster.RasterProperties;
import vn.minhtri.xuongin.raster.RasterSize;
import vn.minhtri.xuongin.raster.SheetRasterizer;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.storage.FileStore;

/**
 * Builds the TIF of one sheet from its print PDF (invariant 10): compose the PDF to disk, refuse
 * early when the sheet does not fit in memory ({@code TIFF_TOO_LARGE}), render the ink mask in
 * bands, then render again in bands straight into the encoder. Only the 1-byte mask is sheet-sized.
 */
@Component
public class SheetTifBuilder {

    private static final Logger log = LoggerFactory.getLogger(SheetTifBuilder.class);

    private final ExportStore store;
    private final FileStore files;
    private final RasterMemoryGuard guard;
    private final SheetRasterizer rasterizer;
    private final RasterProperties rasterProperties;
    private final TifEncoderFactory encoders;
    private final ExportProperties properties;
    private final SourceImageCost sourceImages;

    public SheetTifBuilder(ExportStore store, FileStore files, RasterMemoryGuard guard, SheetRasterizer rasterizer,
            RasterProperties rasterProperties, TifEncoderFactory encoders, ExportProperties properties,
            SourceImageCost sourceImages) {
        this.sourceImages = sourceImages;
        this.store = store;
        this.files = files;
        this.guard = guard;
        this.rasterizer = rasterizer;
        this.rasterProperties = rasterProperties;
        this.encoders = encoders;
        this.properties = properties;
    }

    /**
     * Writes {@code target} (atomically, by the encoder) and returns it. Reserves its share of the
     * shared raster budget first (cut files may be building on request threads at the same time).
     *
     * @throws vn.minhtri.xuongin.shared.ApiException {@code TIFF_TOO_LARGE} when the sheet does not
     *         fit or the heap ran out anyway, {@code JOB_NOT_READY} when the budget stayed full
     */
    public TifResult build(Job job, Sheet sheet, Path target) throws IOException {
        int dpi = properties.tifDpi();
        RasterSize size = guard.check(sheet.index(), sheet.widthMm(), sheet.lengthMm(), dpi);
        long need = guard.estimateBytes(size.width(), size.height()) + sourceImages.of(sheet);
        try (RasterMemoryGuard.Reservation ignored = guard.reserve(need, properties.waitTimeoutMs(),
                () -> busy(sheet.index()))) {
            // Composing decodes image sources too, so it runs inside the reservation.
            Path pdf = store.printPdf(job, sheet);
            return render(sheet, pdf, size, dpi, target);
        } catch (OutOfMemoryError e) {
            // The big arrays are unreachable once render() unwound: report it, keep the worker alive.
            log.warn("TIF of sheet {} ran out of memory ({} x {} px)", sheet.index() + 1, size.width(), size.height());
            throw RasterMemoryGuard.outOfMemory(sheet.index(), "TIF");
        }
    }

    private TifResult render(Sheet sheet, Path pdf, RasterSize size, int dpi, Path target) throws IOException {
        Function<String, StoredFile> lookup = id -> files.find(id).orElse(null);
        byte[] mask = InkMask.build(rasterizer, pdf, dpi, rasterProperties, sheet.widthMm(), sheet.lengthMm(),
                PdfComposer.opaqueImageZones(sheet, lookup, dpi, size.height()));
        try (TifEncoder encoder = encoders.open(target, size.width(), size.height(), dpi, mask)) {
            rasterizer.render(pdf, dpi, encoder);
            return encoder.finish();
        }
    }

    private static ApiException busy(int index) {
        return new ApiException(ErrorCode.JOB_NOT_READY, "Máy chủ đang bận dựng file khác nên chưa dựng được file TIF tấm "
                + (index + 1) + ". Hãy thử lại sau ít phút.", Map.of("sheetIndex", index));
    }
}
