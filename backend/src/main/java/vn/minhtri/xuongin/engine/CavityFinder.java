package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.List;
import vn.minhtri.xuongin.file.OccupancyMask;
import vn.minhtri.xuongin.shared.Units;

/**
 * Finds the empty rectangles inside a design's box (spec 8.4): grow the artwork by one gap, then take up to
 * four largest empty rectangles (largest rectangle in a histogram, row by row), dropping any side under 8 mm.
 */
final class CavityFinder {

    static final int MAX_CAVITIES = 4;
    /** 8 mm. */
    static final long MIN_SIDE = 800;

    private CavityFinder() {}

    /** Cell size of the mask in 1/100 mm, or 0 when it is not a whole number (then the mask is not used). */
    static long cellCenti(OccupancyMask mask) {
        long cell = Units.mmToCenti(mask.cellMm());
        return cell > 0 && Math.abs(Units.centiToMm(cell) - mask.cellMm()) < 1e-9 ? cell : 0;
    }

    /**
     * @param mask artwork grid of the design, or null (solid)
     * @param w    real width of the box, 1/100 mm
     * @param h    real height of the box
     * @param gap  minimum distance to keep from the artwork
     */
    static List<Cavity> find(OccupancyMask mask, long w, long h, long gap) {
        if (mask == null) {
            return List.of();
        }
        long cell = cellCenti(mask);
        if (cell == 0) {
            return List.of();
        }
        int cols = (int) ((w + cell - 1) / cell);
        int rows = (int) ((h + cell - 1) / cell);
        boolean[][] blocked = dilate(mask, cols, rows, (int) ((gap + cell - 1) / cell));
        int minCols = (int) ((MIN_SIDE + cell - 1) / cell);
        int minRows = minCols;

        List<Cavity> cavities = new ArrayList<>();
        for (int n = 0; n < MAX_CAVITIES; n++) {
            int[] best = largestEmpty(blocked, cols, rows, minCols, minRows);
            if (best == null) {
                break;
            }
            long x = best[0] * cell;
            long y = best[1] * cell;
            long right = Math.min((long) best[2] * cell, w);
            long top = Math.min((long) best[3] * cell, h);
            if (right - x >= MIN_SIDE && top - y >= MIN_SIDE) {
                cavities.add(new Cavity(x, y, right - x, top - y));
            }
            for (int r = best[1]; r < best[3]; r++) {
                for (int c = best[0]; c < best[2]; c++) {
                    blocked[r][c] = true;
                }
            }
        }
        return List.copyOf(cavities);
    }

    /** Artwork cells (out-of-grid parts of the box count as artwork) grown by {@code radius} cells. */
    private static boolean[][] dilate(OccupancyMask mask, int cols, int rows, int radius) {
        boolean[][] blocked = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!mask.isOccupied(c, r)) {
                    continue;
                }
                for (int rr = Math.max(0, r - radius); rr <= Math.min(rows - 1, r + radius); rr++) {
                    for (int cc = Math.max(0, c - radius); cc <= Math.min(cols - 1, c + radius); cc++) {
                        blocked[rr][cc] = true;
                    }
                }
            }
        }
        return blocked;
    }

    /**
     * Largest empty rectangle with at least minCols x minRows cells, as {col0, row0, col1, row1} (exclusive),
     * or null. Every maximal rectangle shows up as a histogram candidate, so filtering candidates is exact.
     */
    static int[] largestEmpty(boolean[][] blocked, int cols, int rows, int minCols, int minRows) {
        int[] heights = new int[cols];
        int[] best = null;
        long bestArea = 0;
        int[] stack = new int[cols + 1];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                heights[c] = blocked[r][c] ? 0 : heights[c] + 1;
            }
            int top = 0;
            for (int c = 0; c <= cols; c++) {
                int current = c == cols ? 0 : heights[c];
                while (top > 0 && heights[stack[top - 1]] >= current) {
                    int height = heights[stack[--top]];
                    int left = top == 0 ? 0 : stack[top - 1] + 1;
                    int width = c - left;
                    long area = (long) width * height;
                    if (height >= minRows && width >= minCols && area > bestArea) {
                        bestArea = area;
                        best = new int[] {left, r - height + 1, c, r + 1};
                    }
                }
                stack[top++] = c;
            }
        }
        return best;
    }
}
