package vn.minhtri.xuongin.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
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
import vn.minhtri.xuongin.storage.FileStore;

/**
 * Builds the TIF of one sheet from its print PDF (invariant 10): compose the PDF to disk, refuse
 * early when the sheet does not fit in memory ({@code TIFF_TOO_LARGE}), render the ink mask in
 * bands, then render again in bands straight into the encoder. Only the 1-byte mask is sheet-sized.
 */
@Component
public class SheetTifBuilder {

    private final ExportStore store;
    private final FileStore files;
    private final RasterMemoryGuard guard;
    private final SheetRasterizer rasterizer;
    private final RasterProperties rasterProperties;
    private final TifEncoderFactory encoders;
    private final ExportProperties properties;

    public SheetTifBuilder(ExportStore store, FileStore files, RasterMemoryGuard guard, SheetRasterizer rasterizer,
            RasterProperties rasterProperties, TifEncoderFactory encoders, ExportProperties properties) {
        this.store = store;
        this.files = files;
        this.guard = guard;
        this.rasterizer = rasterizer;
        this.rasterProperties = rasterProperties;
        this.encoders = encoders;
        this.properties = properties;
    }

    /** Writes {@code target} (atomically, by the encoder) and returns it. */
    public TifResult build(Job job, Sheet sheet, Path target) throws IOException {
        int dpi = properties.tifDpi();
        RasterSize size = guard.check(sheet.index(), sheet.widthMm(), sheet.lengthMm(), dpi);
        Path pdf = store.printPdf(job, sheet);
        Function<String, StoredFile> lookup = id -> files.find(id).orElse(null);
        byte[] mask = InkMask.build(rasterizer, pdf, dpi, rasterProperties, sheet.widthMm(), sheet.lengthMm(),
                PdfComposer.opaqueImageZones(sheet, lookup, dpi, size.height()));
        try (TifEncoder encoder = encoders.open(target, size.width(), size.height(), dpi, mask)) {
            rasterizer.render(pdf, dpi, encoder);
            return encoder.finish();
        }
    }
}
