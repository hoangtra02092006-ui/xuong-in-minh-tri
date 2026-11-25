package vn.minhtri.xuongin.file;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Arrays;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import vn.minhtri.xuongin.shared.Units;

/**
 * Builds the occupancy mask of a PDF content box (spec 7.1 step 6) by rendering the page with a
 * transparent background: any pixel with alpha &gt; 0 marks its cell, so white fills count as artwork
 * and anti-aliased edges round the mask outward (the safe side).
 *
 * <p>Memory stays bounded: the page is rendered in horizontal bands of at most {@code bandPixels}
 * pixels at a low resolution ({@code pixelsPerCell} per cell side, 2 px per 0.5 mm ≈ 102 DPI).
 * The mask is in the UNROTATED page space (engine only uses masks when {@code /Rotate} is 0).
 */
final class OccupancyMaskBuilder {

    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final int pixelsPerCell;
    private final int bandPixels;
    private final long maxCells;

    OccupancyMaskBuilder(int pixelsPerCell, int bandPixels, long maxCells) {
        this.pixelsPerCell = Math.max(1, pixelsPerCell);
        this.bandPixels = bandPixels;
        this.maxCells = maxCells;
    }

    /** @return the mask, or null when the box needs more than {@code maxCells} cells (item is then treated as solid) */
    OccupancyMask build(PDDocument doc, ContentBox box, double cellMm) throws IOException {
        int cols = cells(Units.ptToMm(box.widthPt()), cellMm);
        int rows = cells(Units.ptToMm(box.heightPt()), cellMm);
        if ((long) cols * rows > maxCells) {
            return null;
        }
        PDPage page = doc.getPage(0);
        PDRectangle originalCrop = page.getCropBox();
        int originalRotation = page.getRotation();
        page.setCropBox(new PDRectangle((float) box.xPt(), (float) box.yPt(), (float) box.widthPt(), (float) box.heightPt()));
        page.setRotation(0);
        try {
            return render(doc, cols, rows, cellMm);
        } finally {
            page.setCropBox(originalCrop);
            page.setRotation(originalRotation);
        }
    }

    private OccupancyMask render(PDDocument doc, int cols, int rows, double cellMm) throws IOException {
        OccupancyMask mask = OccupancyMask.empty(cols, rows, cellMm);
        float pixelsPerPoint = (float) (pixelsPerCell / Units.mmToPt(cellMm));
        int widthPx = cols * pixelsPerCell;
        int heightPx = rows * pixelsPerCell;
        int bandCells = Math.max(1, bandPixels / (widthPx * pixelsPerCell));
        int bandPx = Math.min(heightPx, bandCells * pixelsPerCell);

        PDFRenderer renderer = new PDFRenderer(doc);
        BufferedImage band = new BufferedImage(widthPx, bandPx, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) band.getRaster().getDataBuffer()).getData();
        for (int top = 0; top < heightPx; top += bandPx) {
            Arrays.fill(pixels, 0);
            Graphics2D g = band.createGraphics();
            try {
                // renderPageToGraphics clears the page with the background colour: keep it transparent.
                g.setBackground(TRANSPARENT);
                g.translate(0, -top);
                renderer.renderPageToGraphics(0, g, pixelsPerPoint);
            } finally {
                g.dispose();
            }
            int bandRows = Math.min(bandPx, heightPx - top);
            for (int y = 0; y < bandRows; y++) {
                // Image rows run top-down; mask row 0 is the bottom row.
                int maskRow = rows - 1 - (top + y) / pixelsPerCell;
                int offset = y * widthPx;
                for (int x = 0; x < widthPx; x++) {
                    if (pixels[offset + x] >>> 24 != 0) {
                        mask.set(x / pixelsPerCell, maskRow);
                    }
                }
            }
        }
        return mask;
    }

    private static int cells(double mm, double cellMm) {
        // Small epsilon so 30.0 mm / 0.5 mm stays 60 cells despite floating point noise.
        return Math.max(1, (int) Math.ceil(mm / cellMm - 1e-4));
    }
}
