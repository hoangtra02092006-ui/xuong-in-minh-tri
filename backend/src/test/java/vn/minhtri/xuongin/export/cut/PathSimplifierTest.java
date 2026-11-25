package vn.minhtri.xuongin.export.cut;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathSimplifierTest {

    @Test
    void zeroToleranceKeepsEveryCorner() {
        int[] square = {0, 0, 10, 0, 10, 10, 0, 10};
        assertThat(PathSimplifier.simplify(square, 4, 0)).containsExactly(0, 1, 2, 3);
    }

    @Test
    void staircaseWithinToleranceCollapsesToTheTriangle() {
        // Lower-left triangle traced as a 1 px staircase: (0,0) (1,0) (1,1) (2,1) ... (5,5) (0,5).
        int steps = 5;
        int n = 2 * steps + 2;
        int[] xy = new int[2 * n];
        int k = 0;
        xy[k++] = 0;
        xy[k++] = 0;
        for (int i = 1; i <= steps; i++) {
            xy[k++] = i;
            xy[k++] = i - 1;
            xy[k++] = i;
            xy[k++] = i;
        }
        xy[k++] = 0;
        xy[k] = steps;
        int[] kept = PathSimplifier.simplify(xy, n, 1.0);
        // Steps deviate 0.71 px from the diagonal: only the three triangle corners stay.
        assertThat(kept).hasSize(3);
        assertThat(kept).contains(0, n - 1);
    }

    @Test
    void cornersBeyondToleranceStay() {
        int[] l = {0, 0, 2, 0, 2, 8, 8, 8, 8, 10, 0, 10};
        assertThat(PathSimplifier.simplify(l, 6, 1.0)).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void thinSliverKeepsAtLeastThreeCorners() {
        // 10 x 1 px sliver: with a 2 px tolerance both halves collapse onto the long chord.
        int[] sliver = {0, 0, 10, 0, 10, 1, 0, 1};
        assertThat(PathSimplifier.simplify(sliver, 4, 2.0)).hasSize(3);
    }

    @Test
    void triangleIsReturnedAsIs() {
        int[] tri = {0, 0, 4, 0, 0, 4};
        assertThat(PathSimplifier.simplify(tri, 3, 5.0)).containsExactly(0, 1, 2);
    }
}
