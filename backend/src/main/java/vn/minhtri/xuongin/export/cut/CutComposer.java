package vn.minhtri.xuongin.export.cut;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDSeparation;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.export.cut.CutProperties.Corner;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.InkMask;
import vn.minhtri.xuongin.raster.PixelRect;
import vn.minhtri.xuongin.raster.RasterMemoryGuard;
import vn.minhtri.xuongin.raster.RasterProperties;
import vn.minhtri.xuongin.raster.RasterSize;
import vn.minhtri.xuongin.raster.SheetRasterizer;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.shared.Units;

/**
 * Builds the CUT file of one sheet for the Graphtec CE7000-60 / Cutting Master (spec 10.5): a
 * separate vector PDF, not a print file, with two layers (OCG):
 * <ul>
 *   <li>{@value #CUT_LAYER}: closed cut lines, stroked (never filled) in a Separation colour
 *       named {@code app.cut.spot-name} (default CutContour, alternate K100);</li>
 *   <li>{@value #MARK_LAYER}: four filled K100 registration marks on the page corners.</li>
 * </ul>
 *
 * <p>Invariant 10: the cut lines come from the composed sheet PDF itself, rasterized with the
 * same {@link SheetRasterizer} and the same {@link InkMask} (raw, NOT choked) as the TIF, then
 * dilated by {@code offsetMm} on the mask, traced (outer + holes), dust-filtered and simplified.
 *
 * <p>Memory (spec 10.6): rendering is banded; the mask (1 byte per pixel) is the only
 * whole-sheet array, plus two transient ones while dilating when {@code offsetMm > 0}.
 * The tracer keeps its bookkeeping inside the mask bytes. The check runs before rendering.
 */
@Component
public class CutComposer {

    static final String CUT_LAYER = "CutContour";
    static final String MARK_LAYER = "RegMarks";
    static final String ARTWORK_LAYER = "Artwork";

    /** Alternate (preview) colour of the spot: K100. The cutter reads the NAME, not this. */
    private static final float[] SPOT_ALTERNATE_CMYK = {0f, 0f, 0f, 1f};

    /** Rendered band: tall ARGB image with overlap rows + the band copy handed to the mask. */
    private static final int BAND_BYTES_PER_PIXEL = 8;

    /** Overlap rows SheetRasterizer renders around each band (2 × 32). */
    private static final int BAND_OVERLAP_ROWS = 64;

    private static final int MIN_SUGGESTED_DPI = 50;
    private static final int DPI_STEP = 10;
    private static final long MB = 1024L * 1024L;

    private static final Logger log = LoggerFactory.getLogger(CutComposer.class);

    private final SheetRasterizer rasterizer;
    private final RasterProperties rasterProperties;
    private final RasterMemoryGuard memoryGuard;
    private final CutProperties properties;

    public CutComposer(SheetRasterizer rasterizer, RasterProperties rasterProperties,
            RasterMemoryGuard memoryGuard, CutProperties properties) {
        this.rasterizer = rasterizer;
        this.rasterProperties = rasterProperties;
        this.memoryGuard = memoryGuard;
        this.properties = properties;
    }

    /**
     * Writes the cut file of {@code sheet} to {@code out} (not closed).
     *
     * @param sheet    the sheet: page width and length come from here, never from pixel counts
     * @param files    file id → stored file (to find pieces from opaque images, spec 10.3)
     * @param sheetPdf the sheet composed by {@link PdfComposer} with {@code drawCutLines = false}
     *                 (with the grey frames on, the cut line would run around the frames)
     * @return page size and contour counts, also logged for checks against Cutting Master
     * @throws ApiException {@code TIFF_TOO_LARGE} when the mask would not fit in memory
     * @throws IllegalArgumentException when {@code sheetPdf} is not the size of {@code sheet}
     */
    public CutResult compose(Sheet sheet, Function<String, StoredFile> files, Path sheetPdf, OutputStream out)
            throws IOException {
        long started = System.nanoTime();
        int dpi = properties.dpi();
        RasterSize size = SheetRasterizer.sizeOf(sheet.widthMm(), sheet.lengthMm(), dpi);
        checkMemory(sheet, size, dpi);

        byte[] mask = buildMask(sheet, files, sheetPdf, size, dpi);
        double padMm = CornerClearance.paddingMm(mask, size.width(), size.height(),
                sheet.widthMm(), sheet.lengthMm(), dpi, properties.cornerZoneMm());
        Contours contours = trace(mask, size, dpi, Units.mmToPt(sheet.lengthMm() + padMm));

        CutResult result = new CutResult(sheet.widthMm(), sheet.lengthMm() + 2 * padMm, padMm,
                contours.outer, contours.holes, contours.dropped);
        writePdf(result, contours.paths, sheetPdf, out);
        logResult(sheet, result, size, dpi, (System.nanoTime() - started) / 1_000_000);
        return result;
    }

    private byte[] buildMask(Sheet sheet, Function<String, StoredFile> files, Path sheetPdf, RasterSize size,
            int dpi) throws IOException {
        List<PixelRect> opaqueZones = PdfComposer.opaqueImageZones(sheet, files, dpi, size.height());
        InkMask inkMask = InkMask.of(size, rasterProperties);
        RasterSize rendered = rasterizer.render(sheetPdf, dpi, inkMask);
        if (!rendered.equals(size)) {
            throw new IllegalArgumentException("sheet PDF renders " + rendered + " but sheet "
                    + sheet.widthMm() + "x" + sheet.lengthMm() + " mm needs " + size);
        }
        // Raw mask, NOT choked: a choked mask breaks thin strokes into 1 px specks that the
        // dust filter then removes (real bug: small text "NGUYÊN BẢN TRÀ VIỆT" vanished).
        byte[] mask = inkMask.finish(opaqueZones);
        int radius = (int) Math.round(properties.offsetMm() * dpi / 25.4);
        return radius > 0 ? InkMask.dilate(mask, size.width(), size.height(), radius) : mask;
    }

    /** Traces, filters and simplifies; output in page points, y up, shifted by the padding. */
    private Contours trace(byte[] mask, RasterSize size, int dpi, double topPt) {
        double ptPerPx = 72.0 / dpi;
        double pxPerMm = dpi / 25.4;
        double minDoubledArea = 2 * properties.minAreaMm2() * pxPerMm * pxPerMm;
        double tolerancePx = properties.simplifyMm() * pxPerMm;
        Contours contours = new Contours();
        ContourTracer.trace(mask, size.width(), size.height(), (xy, n, doubledArea) -> {
            if (Math.abs(doubledArea) < minDoubledArea) {
                contours.dropped++;
                return;
            }
            if (doubledArea < 0) {
                contours.holes++;
            } else {
                contours.outer++;
            }
            int[] kept = PathSimplifier.simplify(xy, n, tolerancePx);
            float[] path = new float[kept.length * 2];
            for (int k = 0; k < kept.length; k++) {
                path[2 * k] = (float) (xy[2 * kept[k]] * ptPerPx);
                path[2 * k + 1] = (float) (topPt - xy[2 * kept[k] + 1] * ptPerPx);
            }
            contours.paths.add(path);
        });
        return contours;
    }

    private void writePdf(CutResult result, List<float[]> paths, Path sheetPdf, OutputStream out)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(
                    (float) Units.mmToPt(result.pageWidthMm()), (float) Units.mmToPt(result.pageLengthMm())));
            document.addPage(page);

            PDOptionalContentProperties layers = new PDOptionalContentProperties();
            PDOptionalContentGroup cutLayer = new PDOptionalContentGroup(CUT_LAYER);
            PDOptionalContentGroup markLayer = new PDOptionalContentGroup(MARK_LAYER);
            layers.addGroup(cutLayer);
            layers.addGroup(markLayer);
            PDOptionalContentGroup artworkLayer = null;
            if (properties.artworkLayer()) {
                artworkLayer = new PDOptionalContentGroup(ARTWORK_LAYER);
                hideFromPrint(artworkLayer);
                layers.addGroup(artworkLayer);
                layers.setGroupEnabled(artworkLayer, false);
            }
            document.getDocumentCatalog().setOCProperties(layers);

            try (PDDocument source = artworkLayer == null ? null : Loader.loadPDF(sheetPdf.toFile());
                    PDPageContentStream content = new PDPageContentStream(document, page)) {
                if (artworkLayer != null) {
                    drawArtwork(content, new LayerUtility(document).importPageAsForm(source, 0),
                            artworkLayer, result.paddingMm());
                }
                drawContours(content, paths, cutLayer);
                drawMarks(content, result, markLayer);
            }
            document.save(out);
        }
    }

    private void drawContours(PDPageContentStream content, List<float[]> paths, PDOptionalContentGroup layer)
            throws IOException {
        content.beginMarkedContent(COSName.OC, layer);
        content.saveGraphicsState();
        content.setStrokingColor(new PDColor(new float[] {1f}, spotColour(properties.spotName())));
        content.setLineWidth(properties.strokePt().floatValue());
        for (float[] path : paths) {
            content.moveTo(path[0], path[1]);
            for (int i = 2; i < path.length; i += 2) {
                content.lineTo(path[i], path[i + 1]);
            }
            content.closePath();
        }
        // One stroke for all paths: every subpath is closed, never filled.
        if (!paths.isEmpty()) {
            content.stroke();
        }
        content.restoreGraphicsState();
        content.endMarkedContent();
    }

    private void drawMarks(PDPageContentStream content, CutResult result, PDOptionalContentGroup layer)
            throws IOException {
        content.beginMarkedContent(COSName.OC, layer);
        content.saveGraphicsState();
        content.setNonStrokingColor(new PDColor(SPOT_ALTERNATE_CMYK, PDDeviceCMYK.INSTANCE));
        for (Corner corner : Corner.values()) {
            for (RegistrationMarks.Rect rect : RegistrationMarks.at(corner, properties.shapeAt(corner),
                    result.pageWidthMm(), result.pageLengthMm(),
                    properties.markLengthMm(), properties.markThicknessMm())) {
                content.addRect((float) Units.mmToPt(rect.x()), (float) Units.mmToPt(rect.y()),
                        (float) Units.mmToPt(rect.w()), (float) Units.mmToPt(rect.h()));
            }
        }
        content.fill();
        content.restoreGraphicsState();
        content.endMarkedContent();
    }

    private static void drawArtwork(PDPageContentStream content, PDFormXObject form,
            PDOptionalContentGroup layer, double padMm) throws IOException {
        content.beginMarkedContent(COSName.OC, layer);
        content.saveGraphicsState();
        content.transform(Matrix.getTranslateInstance(0, (float) Units.mmToPt(padMm)));
        content.drawForm(form);
        content.restoreGraphicsState();
        content.endMarkedContent();
    }

    /** /Usage /Print /PrintState /OFF: viewers that honour it never print the artwork layer. */
    private static void hideFromPrint(PDOptionalContentGroup group) {
        COSDictionary print = new COSDictionary();
        print.setItem(COSName.getPDFName("PrintState"), COSName.OFF);
        COSDictionary usage = new COSDictionary();
        usage.setItem(COSName.getPDFName("Print"), print);
        group.getCOSObject().setItem(COSName.getPDFName("Usage"), usage);
    }

    /**
     * Separation colour {@code name} with alternate DeviceCMYK, tint 0 → no ink, 1 → K100.
     * Illustrator and Cutting Master recognise the cut line by this name.
     */
    static PDSeparation spotColour(String name) throws IOException {
        COSDictionary tint = new COSDictionary();
        tint.setInt(COSName.FUNCTION_TYPE, 2);
        tint.setItem(COSName.DOMAIN, floats(0f, 1f));
        tint.setItem(COSName.C0, floats(0f, 0f, 0f, 0f));
        tint.setItem(COSName.C1, floats(SPOT_ALTERNATE_CMYK));
        tint.setItem(COSName.N, COSInteger.ONE);

        COSArray separation = new COSArray();
        separation.add(COSName.SEPARATION);
        separation.add(COSName.getPDFName(name));
        separation.add(COSName.DEVICECMYK);
        separation.add(tint);
        return new PDSeparation(separation);
    }

    private static COSArray floats(float... values) {
        COSArray array = new COSArray();
        for (float value : values) {
            array.add(new COSFloat(value));
        }
        return array;
    }

    /**
     * Peak heap bytes: the mask (×3 while dilating) plus one rendered band. Much less than the
     * TIF (no colour conversion, no 5-channel samples), hence its own estimate.
     */
    static long estimateBytes(RasterSize size, int bandRows, boolean dilating) {
        long sheet = size.pixels() * (dilating ? 3 : 1);
        long bandHeight = Math.min((long) bandRows + BAND_OVERLAP_ROWS, size.height());
        return sheet + bandHeight * size.width() * BAND_BYTES_PER_PIXEL;
    }

    private boolean fits(RasterSize size, boolean dilating) {
        return size.pixels() <= (long) rasterProperties.maxMegapixels() * 1_000_000L
                && estimateBytes(size, rasterizer.bandRows(), dilating) <= memoryGuard.allowedBytes();
    }

    private void checkMemory(Sheet sheet, RasterSize size, int dpi) {
        boolean dilating = properties.offsetMm() > 0;
        if (fits(size, dilating)) {
            return;
        }
        Integer suggestedDpi = null;
        for (int candidate = (dpi - 1) / DPI_STEP * DPI_STEP; candidate >= MIN_SUGGESTED_DPI; candidate -= DPI_STEP) {
            if (fits(SheetRasterizer.sizeOf(sheet.widthMm(), sheet.lengthMm(), candidate), dilating)) {
                suggestedDpi = candidate;
                break;
            }
        }
        long needMb = estimateBytes(size, rasterizer.bandRows(), dilating) / MB;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sheetIndex", sheet.index());
        details.put("dpi", dpi);
        details.put("widthPx", size.width());
        details.put("heightPx", size.height());
        details.put("estimatedMb", needMb);
        details.put("allowedMb", memoryGuard.allowedBytes() / MB);
        if (suggestedDpi != null) {
            details.put("suggestedDpi", suggestedDpi);
        }
        String advice = suggestedDpi != null
                ? " Hãy đặt APP_CUT_DPI khoảng " + suggestedDpi + " hoặc giảm chiều dài tối đa mỗi file."
                : " Hãy giảm chiều dài tối đa mỗi file.";
        throw new ApiException(ErrorCode.TIFF_TOO_LARGE, String.format(Locale.ROOT,
                "Tấm %d quá lớn để dựng file cắt ở %d DPI: %d × %d điểm ảnh, cần khoảng %d MB bộ nhớ.",
                sheet.index() + 1, dpi, size.width(), size.height(), needMb) + advice, details);
    }

    private void logResult(Sheet sheet, CutResult result, RasterSize size, int dpi, long millis) {
        double w = Units.round2(result.pageWidthMm());
        double l = Units.round2(result.pageLengthMm());
        log.info("File cắt tấm {}: trang {} x {} mm (bản in {} x {} mm, nới {} mm mỗi đầu), {} x {} px @ {} DPI, {} ms",
                sheet.index() + 1, w, l, Units.round2(sheet.widthMm()), Units.round2(sheet.lengthMm()),
                result.paddingMm(), size.width(), size.height(), dpi, millis);
        log.info("  Đường cắt: {} viền ngoài, {} viền lỗ, bỏ {} mảng < {} mm² (nở {} mm)",
                result.outerContours(), result.holeContours(), result.droppedSpecks(),
                properties.minAreaMm2(), properties.offsetMm());
        log.info("  4 dấu định vị cạnh {} mm dày {} mm, góc ngoài tại (0, 0) ({}, 0) (0, {}) ({}, {}) mm;"
                        + " kiểu TL={} TR={} BL={} BR={}",
                properties.markLengthMm(), properties.markThicknessMm(), w, l, w, l,
                properties.markTopLeft(), properties.markTopRight(),
                properties.markBottomLeft(), properties.markBottomRight());
    }

    /** Traced contours in page points plus counts. */
    private static final class Contours {
        final List<float[]> paths = new ArrayList<>();
        int outer;
        int holes;
        int dropped;
    }
}
