package vn.minhtri.xuongin.raster;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.shared.Units;

/**
 * Renders page 1 of a composed sheet PDF in horizontal bands (spec 10.6, invariant 10:
 * TIF and cut file rasterize from the PDF itself).
 *
 * <p>Memory stays bounded by one band whatever the sheet length. Each band is cut by an
 * integer-pixel translation of the whole page (never by shrinking the CropBox, which shifts
 * by a fraction of a pixel), and is rendered with {@link #OVERLAP} extra rows on each side so
 * anti-aliasing at the band edge matches a single-pass render.
 */
@Component
public class SheetRasterizer {

    /** Band height is rounded up to this multiple (TIFF strips are 64 rows). */
    public static final int BAND_MULTIPLE = 64;

    /** Extra rows rendered above and below each band, then discarded. */
    static final int OVERLAP = 32;

    /** A fresh ARGB image is opaque black; PDFBox clears with the background colour. */
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final int bandRows;

    public SheetRasterizer(RasterProperties properties) {
        this.bandRows = roundBandRows(properties.bandRows());
    }

    /** Band height actually used: at least 64, rounded up to a multiple of 64. */
    public int bandRows() {
        return bandRows;
    }

    static int roundBandRows(int requested) {
        int rows = Math.max(requested, 1);
        return (rows + BAND_MULTIPLE - 1) / BAND_MULTIPLE * BAND_MULTIPLE;
    }

    /**
     * Pixel count for a page edge, with exactly PDFBox's own float arithmetic so the size
     * agrees with {@link PDFRenderer#renderImage} to the pixel.
     */
    public static int pixels(float edgePt, int dpi) {
        float scale = dpi / 72f;
        return (int) Math.max(Math.floor(edgePt * scale), 1);
    }

    /** Pixel size of a sheet of the given mm size, as {@link #render} will produce it. */
    public static RasterSize sizeOf(double widthMm, double lengthMm, int dpi) {
        return new RasterSize(
                pixels((float) Units.mmToPt(widthMm), dpi), pixels((float) Units.mmToPt(lengthMm), dpi));
    }

    /**
     * Renders the sheet top to bottom, one band at a time. Each {@code argb} array is a fresh
     * array of exactly {@code rows × width} pixels starting at index 0; transparent where
     * nothing is drawn.
     *
     * @return the full raster size
     */
    public RasterSize render(Path sheetPdf, int dpi, BandConsumer consumer) throws IOException {
        if (dpi <= 0) {
            throw new IllegalArgumentException("dpi must be positive: " + dpi);
        }
        try (PDDocument document = Loader.loadPDF(sheetPdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            PDRectangle crop = document.getPage(0).getCropBox();
            int width = pixels(crop.getWidth(), dpi);
            int height = pixels(crop.getHeight(), dpi);
            float scale = dpi / 72f;
            for (int y = 0; y < height; y += bandRows) {
                int rows = Math.min(bandRows, height - y);
                consumer.accept(y, rows, width, renderBand(renderer, scale, width, height, y, rows));
            }
            return new RasterSize(width, height);
        }
    }

    private static int[] renderBand(PDFRenderer renderer, float scale, int width, int height, int y, int rows)
            throws IOException {
        int above = Math.min(OVERLAP, y);
        int below = Math.min(OVERLAP, height - (y + rows));
        int tallRows = above + rows + below;

        BufferedImage tall = new BufferedImage(width, tallRows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tall.createGraphics();
        try {
            graphics.setBackground(TRANSPARENT);
            graphics.clearRect(0, 0, width, tallRows);
            graphics.translate(0, -(y - above));
            renderer.renderPageToGraphics(0, graphics, scale, scale, RenderDestination.PRINT);
        } finally {
            graphics.dispose();
        }

        int[] pixels = ((DataBufferInt) tall.getRaster().getDataBuffer()).getData();
        if (tallRows == rows) {
            return pixels;
        }
        // Own array from index 0: consumers index from 0, never through a view with an offset.
        int[] band = new int[rows * width];
        System.arraycopy(pixels, above * width, band, 0, band.length);
        return band;
    }
}
