package vn.minhtri.xuongin.file;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the file module ({@code app.file.*}). Defaults follow spec sections 6.2, 7 and 13. */
@ConfigurationProperties(prefix = "app.file")
public class FileProperties {

    /** Trim PDFs to the real ink bounds (spec APP_TRIM_ENABLED). false = whole page box. */
    private boolean trimEnabled = true;

    /** DPI assumed when an image declares none (spec 7.2). */
    private double defaultImageDpi = 72;

    /** Longest preview side in pixels (spec 6.2). */
    private int previewMaxPx = 360;

    private float previewJpegQuality = 0.82f;

    /** PDF drawing operations read before giving up and using the page box (spec 7.1: 50,000). */
    private int maxShapes = 50_000;

    /** Rendered pixels per mask cell side when building the occupancy mask. */
    private int maskPixelsPerCell = 2;

    /** Pixels rendered at once when building the mask (memory bound: 4 bytes per pixel). */
    private int maskBandPixels = 4_000_000;

    /** Above this many cells no mask is built and the item is treated as solid (always safe). */
    private long maskMaxCells = 40_000_000;

    public boolean isTrimEnabled() {
        return trimEnabled;
    }

    public void setTrimEnabled(boolean trimEnabled) {
        this.trimEnabled = trimEnabled;
    }

    public double getDefaultImageDpi() {
        return defaultImageDpi;
    }

    public void setDefaultImageDpi(double defaultImageDpi) {
        this.defaultImageDpi = defaultImageDpi;
    }

    public int getPreviewMaxPx() {
        return previewMaxPx;
    }

    public void setPreviewMaxPx(int previewMaxPx) {
        this.previewMaxPx = previewMaxPx;
    }

    public float getPreviewJpegQuality() {
        return previewJpegQuality;
    }

    public void setPreviewJpegQuality(float previewJpegQuality) {
        this.previewJpegQuality = previewJpegQuality;
    }

    public int getMaxShapes() {
        return maxShapes;
    }

    public void setMaxShapes(int maxShapes) {
        this.maxShapes = maxShapes;
    }

    public int getMaskPixelsPerCell() {
        return maskPixelsPerCell;
    }

    public void setMaskPixelsPerCell(int maskPixelsPerCell) {
        this.maskPixelsPerCell = maskPixelsPerCell;
    }

    public int getMaskBandPixels() {
        return maskBandPixels;
    }

    public void setMaskBandPixels(int maskBandPixels) {
        this.maskBandPixels = maskBandPixels;
    }

    public long getMaskMaxCells() {
        return maskMaxCells;
    }

    public void setMaskMaxCells(long maskMaxCells) {
        this.maskMaxCells = maskMaxCells;
    }
}
