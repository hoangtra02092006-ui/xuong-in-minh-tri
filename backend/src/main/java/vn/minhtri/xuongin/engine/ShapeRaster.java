package vn.minhtri.xuongin.engine;

import java.util.ArrayList;
import java.util.List;
import vn.minhtri.xuongin.file.OccupancyMask;

/**
 * Builds the TRUE_SHAPE bit masks of one design (spec 8.5). Every rounding goes OUTWARD: a cell that touches
 * artwork is artwork. Erring towards solid only wastes a little paper; erring towards empty overlaps prints.
 */
final class ShapeRaster {

    /** 1 mm grid. */
    static final long CELL = 100;
    static final int[] ANGLES = {0, 90, 180, 270, 45, 135, 225, 315};
    private static final double EPS = 1e-7;

    /**
     * One orientation. The mask frame's origin sits on a sheet cell corner; the real rotated box starts at
     * (dx, dy) inside that frame (non-zero only where the grid is larger than the box), size boxW x boxH.
     */
    record Orientation(int angleDeg, BitGrid mask, BitGrid inflated, int[] bottomProfile,
                       long dx, long dy, long boxW, long boxH) {}

    private ShapeRaster() {}

    static List<Orientation> orientations(OccupancyMask shape, long w, long h, boolean canRotate, long gap) {
        BitGrid base = baseMask(shape, w, h);
        int gapCells = (int) ((gap + CELL - 1) / CELL);
        List<Orientation> result = new ArrayList<>();
        for (int angle : ANGLES) {
            if (angle != 0 && !canRotate) {
                continue;
            }
            Orientation o = angle % 90 == 0 ? rightAngle(base, w, h, angle, gapCells) : diagonal(base, w, h, angle, gapCells);
            result.add(o);
        }
        return result;
    }

    /** 1 mm mask aligned to the box's bottom-left, resampled outward from the design's own mask. */
    static BitGrid baseMask(OccupancyMask shape, long w, long h) {
        int cols = (int) ((w + CELL - 1) / CELL);
        int rows = (int) ((h + CELL - 1) / CELL);
        BitGrid grid = new BitGrid(cols, rows);
        long source = shape == null ? 0 : CavityFinder.cellCenti(shape);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (source == 0 || anySourceCell(shape, source, c * CELL, Math.min((c + 1) * CELL, w),
                        r * CELL, Math.min((r + 1) * CELL, h))) {
                    grid.set(c, r);
                }
            }
        }
        return grid;
    }

    private static boolean anySourceCell(OccupancyMask shape, long cell, long x0, long x1, long y0, long y1) {
        int c1 = (int) ((x1 + cell - 1) / cell);
        int r1 = (int) ((y1 + cell - 1) / cell);
        for (int r = (int) (y0 / cell); r < r1; r++) {
            for (int c = (int) (x0 / cell); c < c1; c++) {
                if (shape.isOccupied(c, r)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Orientation rightAngle(BitGrid base, long w, long h, int angle, int gapCells) {
        BitGrid mask = base.rotatedRightAngle(angle);
        long gridW = base.cols() * CELL;
        long gridH = base.rows() * CELL;
        return switch (angle) {
            case 0 -> orientation(angle, mask, gapCells, 0, 0, w, h);
            case 90 -> orientation(angle, mask, gapCells, gridH - h, 0, h, w);
            case 180 -> orientation(angle, mask, gapCells, gridW - w, gridH - h, w, h);
            default -> orientation(angle, mask, gapCells, 0, gridW - w, h, w);
        };
    }

    private static Orientation orientation(int angle, BitGrid mask, int gapCells, long dx, long dy, long bw, long bh) {
        return new Orientation(angle, mask, mask.dilatedExpanding(gapCells), mask.bottomProfile(), dx, dy, bw, bh);
    }

    /**
     * Diagonal angle: every run of artwork cells is a rectangle; it is rotated exactly and rasterised
     * conservatively (each grid row takes the full x-range of the rotated rectangle within that row).
     */
    private static Orientation diagonal(BitGrid base, long w, long h, int angle, int gapCells) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double[] box = rotatedBounds(w, h, cos, sin);
        double boxW = box[2] - box[0];
        double boxH = box[3] - box[1];
        int cols = Math.max(1, (int) Math.ceil(boxW / CELL - EPS));
        int rows = Math.max(1, (int) Math.ceil(boxH / CELL - EPS));
        BitGrid mask = new BitGrid(cols, rows);

        for (int r = 0; r < base.rows(); r++) {
            int c = 0;
            while (c < base.cols()) {
                if (!base.get(c, r)) {
                    c++;
                    continue;
                }
                int start = c;
                while (c < base.cols() && base.get(c, r)) {
                    c++;
                }
                double[][] quad = rotateRect(start * CELL, Math.min(c * CELL, w),
                        r * CELL, Math.min((r + 1) * CELL, h), cos, sin, box[0], box[1]);
                rasterise(quad, mask);
            }
        }
        return orientation(angle, mask, gapCells, 0, 0, Math.round(boxW), Math.round(boxH));
    }

    /** {minX, minY, maxX, maxY} of the box [0,w]x[0,h] after rotation. */
    static double[] rotatedBounds(long w, long h, double cos, double sin) {
        double[] xs = {0, w * cos, -h * sin, w * cos - h * sin};
        double[] ys = {0, w * sin, h * cos, w * sin + h * cos};
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    private static double[][] rotateRect(long x0, long x1, long y0, long y1, double cos, double sin,
                                         double offX, double offY) {
        long[][] corners = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        double[][] quad = new double[4][2];
        for (int i = 0; i < 4; i++) {
            quad[i][0] = corners[i][0] * cos - corners[i][1] * sin - offX;
            quad[i][1] = corners[i][0] * sin + corners[i][1] * cos - offY;
        }
        return quad;
    }

    /** Marks every cell the convex polygon touches (row by row: clip each edge to the row band). */
    private static void rasterise(double[][] poly, BitGrid mask) {
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : poly) {
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        int rowFrom = Math.max(0, (int) Math.floor(minY / CELL - EPS));
        int rowTo = Math.min(mask.rows() - 1, (int) Math.ceil(maxY / CELL + EPS) - 1);
        for (int row = rowFrom; row <= rowTo; row++) {
            double b0 = row * CELL - EPS * CELL;
            double b1 = (row + 1) * CELL + EPS * CELL;
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            for (int i = 0; i < poly.length; i++) {
                double[] p = poly[i];
                double[] q = poly[(i + 1) % poly.length];
                double[] clipped = clipToBand(p, q, b0, b1);
                if (clipped != null) {
                    lo = Math.min(lo, Math.min(clipped[0], clipped[1]));
                    hi = Math.max(hi, Math.max(clipped[0], clipped[1]));
                }
            }
            if (lo > hi) {
                continue;
            }
            int colFrom = Math.max(0, (int) Math.floor(lo / CELL - EPS));
            int colTo = Math.min(mask.cols() - 1, (int) Math.ceil(hi / CELL + EPS) - 1);
            for (int col = colFrom; col <= colTo; col++) {
                mask.set(col, row);
            }
        }
    }

    /** x-coordinates of the part of segment pq with b0 <= y <= b1, or null when there is none. */
    private static double[] clipToBand(double[] p, double[] q, double b0, double b1) {
        double y0 = p[1];
        double y1 = q[1];
        if (Math.max(y0, y1) < b0 || Math.min(y0, y1) > b1) {
            return null;
        }
        if (y0 == y1) {
            return new double[] {p[0], q[0]};
        }
        double t0 = (b0 - y0) / (y1 - y0);
        double t1 = (b1 - y0) / (y1 - y0);
        double ta = Math.max(0, Math.min(t0, t1));
        double tb = Math.min(1, Math.max(t0, t1));
        return new double[] {p[0] + (q[0] - p[0]) * ta, p[0] + (q[0] - p[0]) * tb};
    }
}
