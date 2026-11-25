package vn.minhtri.xuongin.raster;

/**
 * Rectangle in sheet pixels, origin top-left (image convention). Right / bottom edges exclusive.
 * Used to mark the zones of pieces that come from opaque images (spec 10.3).
 */
public record PixelRect(int x0, int y0, int x1, int y1) {

    /** Same rectangle limited to a width x height raster; may become empty. */
    public PixelRect clip(int width, int height) {
        return new PixelRect(
                Math.max(0, x0), Math.max(0, y0), Math.min(width, x1), Math.min(height, y1));
    }

    public boolean isEmpty() {
        return x1 <= x0 || y1 <= y0;
    }

    public boolean contains(int x, int y) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }
}
