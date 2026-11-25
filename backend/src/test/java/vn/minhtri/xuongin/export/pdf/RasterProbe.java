package vn.minhtri.xuongin.export.pdf;

import java.io.IOException;
import java.nio.file.Path;
import vn.minhtri.xuongin.raster.RasterProperties;
import vn.minhtri.xuongin.raster.SheetRasterizer;

/** Whole-sheet render for assertions, with ink bounding boxes measured in sheet mm. */
public final class RasterProbe {

    public final int width;
    public final int height;
    public final int dpi;
    public final int[] argb;

    private RasterProbe(int width, int height, int dpi, int[] argb) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.argb = argb;
    }

    public static RasterProbe render(Path pdf, int dpi) throws IOException {
        SheetRasterizer rasterizer = new SheetRasterizer(new RasterProperties(null, null, 1 << 20, null, null));
        int[][] holder = new int[1][];
        int[] size = new int[2];
        rasterizer.render(pdf, dpi, (y, rows, w, px) -> {
            holder[0] = px;
            size[0] = w;
            size[1] = rows;
        });
        return new RasterProbe(size[0], size[1], dpi, holder[0]);
    }

    public int at(int x, int y) {
        return argb[y * width + x];
    }

    public double pxToMm(double px) {
        return px * 25.4 / dpi;
    }

    public int mmToPx(double mm) {
        return (int) Math.round(mm * dpi / 25.4);
    }

    /**
     * Ink bounding box (alpha > 128) inside a search window given in sheet mm (origin
     * bottom-left). Returns {xMm, yMm, wMm, hMm} in sheet mm, or null when empty.
     */
    public double[] inkBox(double x0Mm, double y0Mm, double x1Mm, double y1Mm) {
        int px0 = Math.max(0, mmToPx(x0Mm));
        int px1 = Math.min(width, mmToPx(x1Mm));
        int py0 = Math.max(0, height - mmToPx(y1Mm));
        int py1 = Math.min(height, height - mmToPx(y0Mm));
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = py0; y < py1; y++) {
            for (int x = px0; x < px1; x++) {
                if (at(x, y) >>> 24 > 128) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        return new double[] {
            pxToMm(minX), pxToMm(height - (maxY + 1)), pxToMm(maxX + 1 - minX), pxToMm(maxY + 1 - minY)
        };
    }

    /** Centroid of red-ish pixels in a window (sheet mm), returned in sheet mm. */
    public double[] redCentroid(double x0Mm, double y0Mm, double x1Mm, double y1Mm) {
        int px0 = Math.max(0, mmToPx(x0Mm));
        int px1 = Math.min(width, mmToPx(x1Mm));
        int py0 = Math.max(0, height - mmToPx(y1Mm));
        int py1 = Math.min(height, height - mmToPx(y0Mm));
        double sx = 0;
        double sy = 0;
        long n = 0;
        for (int y = py0; y < py1; y++) {
            for (int x = px0; x < px1; x++) {
                int p = at(x, y);
                if (p >>> 24 > 200 && ((p >> 16) & 0xFF) > 200 && ((p >> 8) & 0xFF) < 60 && (p & 0xFF) < 60) {
                    sx += x + 0.5;
                    sy += y + 0.5;
                    n++;
                }
            }
        }
        return n == 0 ? null : new double[] {pxToMm(sx / n), pxToMm(height - sy / n)};
    }
}
