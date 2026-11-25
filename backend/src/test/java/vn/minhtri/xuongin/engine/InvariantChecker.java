package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.minhtri.xuongin.file.OccupancyMask;

/**
 * Checks invariants 1-5 and 8 on every engine result (spec 15): count, size kept, inside the margins,
 * no overlap and every pair at least one gap apart (by real ink in FREE / TRUE_SHAPE), max length,
 * rotation lock, and determinism (a second run gives the same coordinates).
 */
final class InvariantChecker {

    private static final double EPS = 1e-6;
    private static final NestingEngine ENGINE = new NestingEngine();

    private InvariantChecker() {}

    /** Runs the engine twice, checks everything, returns the result. */
    static EngineResult nestAndCheck(List<EngineItem> items, EngineParams params) {
        EngineResult first = ENGINE.nest(items, params);
        EngineResult second = ENGINE.nest(items, params);
        assertThat(second).as("invariant 5: same input, same coordinates").isEqualTo(first);
        check(items, params, first);
        return first;
    }

    static void check(List<EngineItem> items, EngineParams params, EngineResult result) {
        int[] counts = new int[items.size()];
        Set<Integer> ids = new HashSet<>();
        for (EngineSheet sheet : result.sheets()) {
            assertThat(sheet.width()).isEqualTo(params.sheetWidth());
            if (params.maxSheetLength() != null) {
                assertThat(sheet.length()).as("invariant 4: max sheet length").isLessThanOrEqualTo(params.maxSheetLength());
            }
            for (EnginePlacement p : sheet.placements()) {
                EngineItem item = items.get(p.itemIndex());
                counts[p.itemIndex()]++;
                assertThat(ids.add(p.pieceId())).as("piece placed once").isTrue();
                checkSizeAndRotation(item, params, p);
                assertThat(p.x()).as("invariant 3: left margin").isGreaterThanOrEqualTo(params.margin());
                assertThat(p.y()).as("invariant 3: bottom margin").isGreaterThanOrEqualTo(params.margin());
                assertThat(p.right()).as("invariant 3: right margin")
                        .isLessThanOrEqualTo(params.sheetWidth() - params.margin());
                assertThat(p.top()).as("invariant 3: top margin").isLessThanOrEqualTo(sheet.length() - params.margin());
            }
            checkSpacing(items, params, sheet);
        }
        for (int i = 0; i < items.size(); i++) {
            assertThat(counts[i]).as("invariant 2: count of item %d", i).isEqualTo(items.get(i).quantity());
        }
    }

    private static void checkSizeAndRotation(EngineItem item, EngineParams params, EnginePlacement p) {
        int angle = p.totalAngleDeg();
        if (!(params.allowRotateGlobal() && item.allowRotate())) {
            assertThat(angle).as("invariant 8: rotation locked").isZero();
        }
        if (params.mode() != EngineMode.TRUE_SHAPE) {
            assertThat(p.angleDeg()).isZero();
        } else {
            assertThat(p.rotated()).isFalse();
        }
        double rad = Math.toRadians(angle);
        double cos = Math.abs(Math.cos(rad));
        double sin = Math.abs(Math.sin(rad));
        long expectedW = Math.round(item.w() * cos + item.h() * sin);
        long expectedH = Math.round(item.w() * sin + item.h() * cos);
        assertThat(p.w()).as("invariant 1: width not scaled").isEqualTo(expectedW);
        assertThat(p.h()).as("invariant 1: height not scaled").isEqualTo(expectedH);
    }

    private static void checkSpacing(List<EngineItem> items, EngineParams params, EngineSheet sheet) {
        List<EnginePlacement> ps = sheet.placements();
        List<List<double[][]>> inks = new ArrayList<>(ps.size());
        for (EnginePlacement p : ps) {
            inks.add(null);
        }
        long gap = params.gap();
        for (int i = 0; i < ps.size(); i++) {
            EnginePlacement a = ps.get(i);
            for (int j = i + 1; j < ps.size(); j++) {
                EnginePlacement b = ps.get(j);
                long dx = Math.max(b.x() - a.right(), a.x() - b.right());
                long dy = Math.max(b.y() - a.top(), a.y() - b.top());
                if (dx >= gap || dy >= gap) {
                    continue;
                }
                assertThat(params.mode()).as("invariant 3: boxes of %s and %s closer than the gap", a, b)
                        .isNotEqualTo(EngineMode.ORTHOGONAL);
                if (inks.get(i) == null) {
                    inks.set(i, ink(items.get(a.itemIndex()), a));
                }
                if (inks.get(j) == null) {
                    inks.set(j, ink(items.get(b.itemIndex()), b));
                }
                double distance = minDistance(inks.get(i), inks.get(j), gap);
                assertThat(distance).as("invariant 3: ink of %s and %s", a, b).isGreaterThanOrEqualTo(gap - EPS);
            }
        }
    }

    /** Real artwork of a placement as convex quads in sheet coordinates. */
    static List<double[][]> ink(EngineItem item, EnginePlacement p) {
        List<long[]> rects = new ArrayList<>();
        OccupancyMask m = item.shape();
        if (m == null) {
            rects.add(new long[] {0, item.w(), 0, item.h()});
        } else {
            long cell = Math.round(m.cellMm() * 100);
            int cols = (int) ((item.w() + cell - 1) / cell);
            int rows = (int) ((item.h() + cell - 1) / cell);
            for (int r = 0; r < rows; r++) {
                int c = 0;
                while (c < cols) {
                    if (!m.isOccupied(c, r)) {
                        c++;
                        continue;
                    }
                    int start = c;
                    while (c < cols && m.isOccupied(c, r)) {
                        c++;
                    }
                    rects.add(new long[] {start * cell, Math.min(c * cell, item.w()), r * cell,
                        Math.min((r + 1) * cell, item.h())});
                }
            }
        }
        double rad = Math.toRadians(p.totalAngleDeg());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double[] bounds = ShapeRaster.rotatedBounds(item.w(), item.h(), cos, sin);
        List<double[][]> quads = new ArrayList<>(rects.size());
        for (long[] r : rects) {
            long[][] corners = {{r[0], r[2]}, {r[1], r[2]}, {r[1], r[3]}, {r[0], r[3]}};
            double[][] q = new double[4][2];
            for (int k = 0; k < 4; k++) {
                q[k][0] = p.x() + corners[k][0] * cos - corners[k][1] * sin - bounds[0];
                q[k][1] = p.y() + corners[k][0] * sin + corners[k][1] * cos - bounds[1];
            }
            quads.add(q);
        }
        return quads;
    }

    /** Minimum distance between two sets of convex quads (stops early once below {@code limit}). */
    static double minDistance(List<double[][]> a, List<double[][]> b, double limit) {
        double best = Double.MAX_VALUE;
        for (double[][] qa : a) {
            double[] ba = aabb(qa);
            for (double[][] qb : b) {
                double[] bb = aabb(qb);
                double ax = Math.max(bb[0] - ba[2], ba[0] - bb[2]);
                double ay = Math.max(bb[1] - ba[3], ba[1] - bb[3]);
                if (Math.max(ax, ay) >= Math.min(best, limit)) {
                    continue;
                }
                best = Math.min(best, quadDistance(qa, qb));
                if (best < limit - EPS) {
                    return best;
                }
            }
        }
        return best;
    }

    private static double[] aabb(double[][] q) {
        double[] r = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : q) {
            r[0] = Math.min(r[0], p[0]);
            r[1] = Math.min(r[1], p[1]);
            r[2] = Math.max(r[2], p[0]);
            r[3] = Math.max(r[3], p[1]);
        }
        return r;
    }

    static double quadDistance(double[][] a, double[][] b) {
        if (intersects(a, b)) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (double[] p : a) {
            for (int k = 0; k < b.length; k++) {
                best = Math.min(best, pointSegment(p, b[k], b[(k + 1) % b.length]));
            }
        }
        for (double[] p : b) {
            for (int k = 0; k < a.length; k++) {
                best = Math.min(best, pointSegment(p, a[k], a[(k + 1) % a.length]));
            }
        }
        return best;
    }

    /** Separating axis test with a small tolerance so that touching counts as intersecting. */
    private static boolean intersects(double[][] a, double[][] b) {
        return !separated(a, b) && !separated(b, a);
    }

    private static boolean separated(double[][] a, double[][] b) {
        for (int k = 0; k < a.length; k++) {
            double[] p = a[k];
            double[] q = a[(k + 1) % a.length];
            double nx = q[1] - p[1];
            double ny = p[0] - q[0];
            double len = Math.hypot(nx, ny);
            if (len == 0) {
                continue;
            }
            double minA = Double.MAX_VALUE;
            double maxA = -Double.MAX_VALUE;
            double minB = Double.MAX_VALUE;
            double maxB = -Double.MAX_VALUE;
            for (double[] v : a) {
                double d = (v[0] * nx + v[1] * ny) / len;
                minA = Math.min(minA, d);
                maxA = Math.max(maxA, d);
            }
            for (double[] v : b) {
                double d = (v[0] * nx + v[1] * ny) / len;
                minB = Math.min(minB, d);
                maxB = Math.max(maxB, d);
            }
            if (maxA < minB - EPS || maxB < minA - EPS) {
                return true;
            }
        }
        return false;
    }

    private static double pointSegment(double[] p, double[] a, double[] b) {
        double vx = b[0] - a[0];
        double vy = b[1] - a[1];
        double len2 = vx * vx + vy * vy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * vx + (p[1] - a[1]) * vy) / len2));
        return Math.hypot(p[0] - a[0] - t * vx, p[1] - a[1] - t * vy);
    }
}
