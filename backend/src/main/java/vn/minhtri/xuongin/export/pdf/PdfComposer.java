package vn.minhtri.xuongin.export.pdf;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.PixelRect;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.shared.Units;

/**
 * Builds the vector print PDF of one sheet (spec 10.1). Source PDFs are embedded as form
 * XObjects (invariant 6, never rasterized); images as image XObjects at their physical size.
 * Each source is embedded once per sheet and reused by every copy.
 *
 * <p>Every PDF piece is drawn with the content box stored at upload (invariant 9), never with
 * {@code form.getBBox()} (always the CropBox): the box is moved to the cell origin and the
 * drawing is clipped to it, so the trimmed margins never print.
 */
@Component
public class PdfComposer {

    /** Spec 10.1: grey 0.25 pt frame around each piece. */
    static final float CUT_LINE_WIDTH_PT = 0.25f;

    static final float CUT_LINE_GREY = 0.6f;

    /** Tolerance so 25.4 mm at 100 DPI is 100 px, not 101 (floating-point noise). */
    private static final double PX_EPSILON = 1e-6;

    /**
     * Writes the sheet as a one-page PDF to {@code out} (not closed).
     *
     * @param files        file id → stored file; null result = unknown file
     * @param paths        stored file → path of its bytes on disk
     * @param drawCutLines grey frame around each piece (the cut file always passes false)
     */
    public void compose(
            Sheet sheet,
            Function<String, StoredFile> files,
            Function<StoredFile, Path> paths,
            boolean drawCutLines,
            OutputStream out)
            throws IOException {
        try (PDDocument output = new PDDocument();
                Sources sources = new Sources(output, paths)) {
            PDPage page = new PDPage(new PDRectangle(
                    (float) Units.mmToPt(sheet.widthMm()), (float) Units.mmToPt(sheet.lengthMm())));
            output.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(output, page)) {
                for (Placement placement : sheet.placements()) {
                    StoredFile file = require(files, placement.fileId());
                    if (file.type() == FileType.PDF) {
                        drawPdf(content, sources.form(file), file, placement);
                    } else {
                        drawImage(content, sources.image(file), placement);
                    }
                    if (drawCutLines) {
                        drawCutLine(content, placement);
                    }
                }
            }
            output.save(out);
        }
    }

    /**
     * Pixel zones (origin top-left) of the pieces that come from images without alpha (JPG,
     * PNG without transparency) on a raster of {@code heightPx} rows at {@code dpi}. Only these
     * zones get the background flood of spec 10.3; rounded outwards so the zone covers the piece.
     */
    public static List<PixelRect> opaqueImageZones(
            Sheet sheet, Function<String, StoredFile> files, int dpi, int heightPx) {
        List<PixelRect> zones = new ArrayList<>();
        for (Placement placement : sheet.placements()) {
            StoredFile file = require(files, placement.fileId());
            if (file.type() != FileType.IMAGE || file.hasAlpha()) {
                continue;
            }
            int x0 = floorPx(placement.xMm(), dpi);
            int x1 = ceilPx(placement.xMm() + placement.wMm(), dpi);
            // Sheet y grows upwards, raster rows grow downwards.
            int y0 = heightPx - ceilPx(placement.yMm() + placement.hMm(), dpi);
            int y1 = heightPx - floorPx(placement.yMm(), dpi);
            zones.add(new PixelRect(x0, y0, x1, y1));
        }
        return zones;
    }

    private static int floorPx(double mm, int dpi) {
        return (int) Math.floor(Units.mmToPt(mm) * dpi / 72.0 + PX_EPSILON);
    }

    private static int ceilPx(double mm, int dpi) {
        return (int) Math.ceil(Units.mmToPt(mm) * dpi / 72.0 - PX_EPSILON);
    }

    private static StoredFile require(Function<String, StoredFile> files, String fileId) {
        StoredFile file = files.apply(fileId);
        if (file == null) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND,
                    "Không tìm thấy file nguồn " + fileId + " (có thể đã bị dọn). Hãy tải file lên lại rồi ghép lại.");
        }
        return file;
    }

    /**
     * Maps a box of {@code targetW × targetH} pt, unrotated, with origin (0,0), into the
     * placement's cell (spec 10.1 steps 1 and 2).
     */
    static AffineTransform cellTransform(Placement placement) {
        AffineTransform transform = new AffineTransform();
        transform.translate(Units.mmToPt(placement.xMm()), Units.mmToPt(placement.yMm()));
        if (placement.rotated()) {
            transform.translate(Units.mmToPt(placement.wMm()), 0);
            transform.rotate(Math.PI / 2);
        } else if (placement.angleDeg() != 0) {
            applyFreeAngle(transform, placement.angleDeg(),
                    Units.mmToPt(placement.sourceWMm()), Units.mmToPt(placement.sourceHMm()));
        }
        return transform;
    }

    /** Rotates by {@code angleDeg} so the rotated box's bottom-left corner lands on (0,0). */
    private static void applyFreeAngle(AffineTransform transform, double angleDeg, double widthPt, double heightPt) {
        double radians = Math.toRadians(angleDeg);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double[] xs = {0, widthPt * cos, -heightPt * sin, widthPt * cos - heightPt * sin};
        double[] ys = {0, widthPt * sin, heightPt * cos, widthPt * sin + heightPt * cos};
        double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double minY = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
        transform.translate(-minX, -minY);
        transform.rotate(radians);
    }

    private static void drawPdf(PDPageContentStream content, PDFormXObject form, StoredFile file, Placement placement)
            throws IOException {
        ContentBox box = Objects.requireNonNull(file.contentBox(), () -> "no stored content box for " + file.id());
        int rotation = Math.floorMod(file.pageRotation(), 360);
        boolean swapped = rotation == 90 || rotation == 270;
        double displayW = swapped ? box.heightPt() : box.widthPt();
        double displayH = swapped ? box.widthPt() : box.heightPt();

        AffineTransform transform = cellTransform(placement);
        // Step 3: the size before rotation; equals the box size (scale 1) unless trimmed sizes differ.
        transform.scale(Units.mmToPt(placement.sourceWMm()) / displayW, Units.mmToPt(placement.sourceHMm()) / displayH);
        applyPageRotation(transform, rotation, box);
        transform.translate(-box.xPt(), -box.yPt());

        content.saveGraphicsState();
        content.transform(new Matrix(transform));
        content.addRect((float) box.xPt(), (float) box.yPt(), (float) box.widthPt(), (float) box.heightPt());
        content.clip();
        content.drawForm(form);
        content.restoreGraphicsState();
    }

    /**
     * /Rotate is lost when a page is embedded elsewhere, so the viewer's clockwise rotation is
     * redone here; maps the box-local (unrotated) area onto the displayed {@code W × H} area.
     */
    private static void applyPageRotation(AffineTransform transform, int rotation, ContentBox box) {
        switch (rotation) {
            case 90 -> {
                transform.translate(0, box.widthPt());
                transform.rotate(-Math.PI / 2);
            }
            case 180 -> {
                transform.translate(box.widthPt(), box.heightPt());
                transform.rotate(Math.PI);
            }
            case 270 -> {
                transform.translate(box.heightPt(), 0);
                transform.rotate(Math.PI / 2);
            }
            default -> {
                // 0: nothing to undo.
            }
        }
    }

    private static void drawImage(PDPageContentStream content, PDImageXObject image, Placement placement)
            throws IOException {
        AffineTransform transform = cellTransform(placement);
        transform.scale(Units.mmToPt(placement.sourceWMm()), Units.mmToPt(placement.sourceHMm()));
        content.saveGraphicsState();
        content.transform(new Matrix(transform));
        content.drawImage(image, 0, 0, 1, 1);
        content.restoreGraphicsState();
    }

    private static void drawCutLine(PDPageContentStream content, Placement placement) throws IOException {
        content.saveGraphicsState();
        content.setStrokingColor(CUT_LINE_GREY, CUT_LINE_GREY, CUT_LINE_GREY);
        content.setLineWidth(CUT_LINE_WIDTH_PT);
        content.addRect(
                (float) Units.mmToPt(placement.xMm()),
                (float) Units.mmToPt(placement.yMm()),
                (float) Units.mmToPt(placement.wMm()),
                (float) Units.mmToPt(placement.hMm()));
        content.stroke();
        content.restoreGraphicsState();
    }

    /** Embeds each source once per sheet and keeps the source documents open until save. */
    private static final class Sources implements AutoCloseable {
        private final PDDocument output;
        private final Function<StoredFile, Path> paths;
        private final LayerUtility layers;
        private final Map<String, PDFormXObject> forms = new HashMap<>();
        private final Map<String, PDImageXObject> images = new HashMap<>();
        private final List<PDDocument> opened = new ArrayList<>();

        Sources(PDDocument output, Function<StoredFile, Path> paths) {
            this.output = output;
            this.paths = paths;
            this.layers = new LayerUtility(output);
        }

        PDFormXObject form(StoredFile file) throws IOException {
            PDFormXObject form = forms.get(file.id());
            if (form == null) {
                PDDocument source = Loader.loadPDF(paths.apply(file).toFile());
                opened.add(source);
                form = layers.importPageAsForm(source, 0);
                // importPageAsForm bakes /Rotate and the CropBox offset into the form matrix.
                // The stored content box is in raw page coordinates and drawPdf undoes /Rotate
                // itself, so keep the form in raw page space (otherwise rotation is applied twice).
                form.setMatrix(new AffineTransform());
                forms.put(file.id(), form);
            }
            return form;
        }

        PDImageXObject image(StoredFile file) throws IOException {
            PDImageXObject image = images.get(file.id());
            if (image == null) {
                image = PDImageXObject.createFromFileByContent(paths.apply(file).toFile(), output);
                images.put(file.id(), image);
            }
            return image;
        }

        @Override
        public void close() throws IOException {
            IOException first = null;
            for (PDDocument document : opened) {
                try {
                    document.close();
                } catch (IOException ex) {
                    if (first == null) {
                        first = ex;
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        }
    }
}
