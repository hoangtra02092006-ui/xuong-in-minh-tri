package vn.minhtri.xuongin.export.cut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContourTracerTest {

    private record Traced(int[] corners, long doubledArea) {}

    /** Mask from rows of '#' (ink) and '.' (none). */
    private static byte[] mask(String... rows) {
        int width = rows[0].length();
        byte[] mask = new byte[width * rows.length];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                mask[y * width + x] = (byte) (rows[y].charAt(x) == '#' ? 1 : 0);
            }
        }
        return mask;
    }

    private static List<Traced> trace(byte[] mask, int width, int height) {
        List<Traced> out = new ArrayList<>();
        ContourTracer.trace(mask, width, height, (xy, n, area) -> out.add(new Traced(Arrays.copyOf(xy, 2 * n), area)));
        return out;
    }

    private static List<Traced> trace(String... rows) {
        return trace(mask(rows), rows[0].length(), rows.length);
    }

    @Test
    void singlePixelIsAUnitSquareOnPixelCorners() {
        List<Traced> contours = trace("...", ".#.", "...");
        assertThat(contours).hasSize(1);
        assertThat(contours.get(0).corners()).containsExactlyInAnyOrder(1, 1, 2, 1, 2, 2, 1, 2);
        assertThat(contours.get(0).doubledArea()).isEqualTo(2);
    }

    @Test
    void straightRunsAreMergedToCorners() {
        List<Traced> contours = trace("####", "####");
        assertThat(contours).hasSize(1);
        assertThat(contours.get(0).corners()).hasSize(8); // 4 corners
        assertThat(contours.get(0).doubledArea()).isEqualTo(16);
    }

    @Test
    void ringGivesPositiveOuterAndNegativeHole() {
        List<Traced> contours = trace(
                "#####",
                "#...#",
                "#...#",
                "#####");
        assertThat(contours).hasSize(2);
        assertThat(contours.get(0).doubledArea()).isEqualTo(2 * 20);
        assertThat(contours.get(1).doubledArea()).isEqualTo(-2 * 6);
    }

    @Test
    void lShapeHasSixCorners() {
        List<Traced> contours = trace(
                "#..",
                "#..",
                "###");
        assertThat(contours).hasSize(1);
        assertThat(contours.get(0).corners()).hasSize(12);
        assertThat(contours.get(0).doubledArea()).isEqualTo(10);
    }

    @Test
    void diagonalTouchIsTwoSeparatePieces() {
        List<Traced> contours = trace("#.", ".#");
        assertThat(contours).hasSize(2);
        assertThat(contours).allSatisfy(c -> assertThat(c.doubledArea()).isEqualTo(2));
    }

    @Test
    void diagonalHolesAreTracedWithoutLoopingForever() {
        List<Traced> contours = trace(
                "####",
                "#.##",
                "##.#",
                "####");
        // Ink is 4-connected → one piece; the two empty pixels touch diagonally → one hole
        // contour pinched at the shared corner.
        long outer = contours.stream().filter(c -> c.doubledArea() > 0).count();
        long holeArea = contours.stream().filter(c -> c.doubledArea() < 0).mapToLong(Traced::doubledArea).sum();
        assertThat(outer).isEqualTo(1);
        assertThat(holeArea).isEqualTo(-4);
    }

    @Test
    void inkTouchingTheSheetEdgeIsClosedAlongTheEdge() {
        List<Traced> contours = trace("##", "##");
        assertThat(contours).hasSize(1);
        assertThat(contours.get(0).corners()).containsExactlyInAnyOrder(0, 0, 2, 0, 2, 2, 0, 2);
    }

    @Test
    void maskIsLeftUnchanged() {
        byte[] mask = mask("#####", "#...#", "#####");
        byte[] before = mask.clone();
        trace(mask, 5, 3);
        assertThat(mask).isEqualTo(before);
    }

    @Test
    void emptyMaskHasNoContours() {
        assertThat(trace("...", "...")).isEmpty();
    }

    @Test
    void rejectsAShortMask() {
        assertThatThrownBy(() -> ContourTracer.trace(new byte[3], 2, 2, (xy, n, a) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void longContourGrowsItsBuffer() {
        // Staircase: every pixel adds two corners, more than the initial 32-corner buffer.
        int size = 60;
        byte[] mask = new byte[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x <= y; x++) {
                mask[y * size + x] = 1;
            }
        }
        List<Traced> contours = trace(mask, size, size);
        assertThat(contours).hasSize(1);
        assertThat(contours.get(0).corners().length / 2).isEqualTo(2 * size + 2);
        assertThat(contours.get(0).doubledArea()).isEqualTo((long) size * (size + 1));
    }
}
