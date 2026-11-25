package vn.minhtri.xuongin.export.cut;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Douglas–Peucker for a closed polygon (spec 10.5 step 6), after the tracer already merged
 * collinear runs. Iterative (a deep contour would overflow a recursive version). The ring is
 * split at vertex 0 and the vertex farthest from it, so both halves are open polylines whose
 * end points are real corners of the shape.
 */
final class PathSimplifier {

    private PathSimplifier() {}

    /**
     * @param xy        corners x0,y0,x1,y1,… (first corner not repeated at the end)
     * @param n         number of corners
     * @param tolerance max distance, same unit as the coordinates; ≤ 0 keeps every corner
     * @return indices of the kept corners, in order; never fewer than 3 when {@code n ≥ 3}
     */
    static int[] simplify(int[] xy, int n, double tolerance) {
        if (n <= 3 || tolerance <= 0) {
            return range(n);
        }
        int far = farthestFrom(xy, n, 0);
        boolean[] keep = new boolean[n + 1];
        keep[0] = true;
        keep[far] = true;
        keep[n] = true;
        reduce(xy, n, 0, far, tolerance, keep);
        reduce(xy, n, far, n, tolerance, keep);

        int count = 0;
        for (int i = 0; i < n; i++) {
            count += keep[i] ? 1 : 0;
        }
        if (count < 3) {
            // Thin sliver: both halves collapsed onto the chord. Keep the corner farthest from
            // that chord so the cut stays a closed shape instead of a zero-area line.
            keep[farthestFromChord(xy, n, far)] = true;
            count++;
        }
        int[] kept = new int[count];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                kept[k++] = i;
            }
        }
        return kept;
    }

    /** Index {@code n} stands for vertex 0 again (closing the ring). */
    private static void reduce(int[] xy, int n, int first, int last, double tolerance, boolean[] keep) {
        Deque<int[]> todo = new ArrayDeque<>();
        todo.push(new int[] {first, last});
        while (!todo.isEmpty()) {
            int[] span = todo.pop();
            int from = span[0];
            int to = span[1];
            if (to - from < 2) {
                continue;
            }
            int worstAt = -1;
            double worst = -1;
            for (int i = from + 1; i < to; i++) {
                double d = distance(xy, n, i, from, to);
                if (d > worst) {
                    worst = d;
                    worstAt = i;
                }
            }
            if (worst > tolerance) {
                keep[worstAt] = true;
                todo.push(new int[] {from, worstAt});
                todo.push(new int[] {worstAt, to});
            }
        }
    }

    /** Distance from vertex p to the segment a–b (indices modulo n). */
    private static double distance(int[] xy, int n, int p, int a, int b) {
        double ax = x(xy, n, a);
        double ay = y(xy, n, a);
        double dx = x(xy, n, b) - ax;
        double dy = y(xy, n, b) - ay;
        double px = x(xy, n, p) - ax;
        double py = y(xy, n, p) - ay;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            return Math.hypot(px, py);
        }
        double t = Math.max(0, Math.min(1, (px * dx + py * dy) / lengthSq));
        return Math.hypot(px - t * dx, py - t * dy);
    }

    private static int farthestFrom(int[] xy, int n, int from) {
        int best = 1;
        long bestSq = -1;
        for (int i = 1; i < n; i++) {
            long dx = (long) xy[2 * i] - xy[2 * from];
            long dy = (long) xy[2 * i + 1] - xy[2 * from + 1];
            long sq = dx * dx + dy * dy;
            if (sq > bestSq) {
                bestSq = sq;
                best = i;
            }
        }
        return best;
    }

    private static int farthestFromChord(int[] xy, int n, int far) {
        int best = far == 1 ? 2 : 1;
        double bestD = -1;
        for (int i = 1; i < n; i++) {
            if (i == far) {
                continue;
            }
            double d = distance(xy, n, i, 0, far);
            if (d > bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static double x(int[] xy, int n, int i) {
        return xy[2 * (i % n)];
    }

    private static double y(int[] xy, int n, int i) {
        return xy[2 * (i % n) + 1];
    }

    private static int[] range(int n) {
        int[] all = new int[n];
        for (int i = 0; i < n; i++) {
            all[i] = i;
        }
        return all;
    }
}
