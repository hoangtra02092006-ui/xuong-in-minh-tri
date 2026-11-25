package vn.minhtri.xuongin.engine;

import java.util.function.BiPredicate;
import vn.minhtri.xuongin.file.OccupancyMask;

/** Hand-made occupancy masks for tests (0.5 mm cells like the real upload). */
final class Masks {

    static final double CELL_MM = OccupancyMask.DEFAULT_CELL_MM;

    private Masks() {}

    /** Mask of a box w x h (1/100 mm) whose cell (col, row) is artwork when {@code ink} says so. */
    static OccupancyMask of(long w, long h, BiPredicate<Integer, Integer> ink) {
        int cols = (int) Math.ceil(w / (CELL_MM * 100));
        int rows = (int) Math.ceil(h / (CELL_MM * 100));
        OccupancyMask mask = OccupancyMask.empty(cols, rows, CELL_MM);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (ink.test(c, r)) {
                    mask.set(c, r);
                }
            }
        }
        return mask;
    }

    /** L shape: a vertical arm on the left and a horizontal arm at the bottom, both {@code arm} thick. */
    static OccupancyMask lShape(long size, long arm) {
        int armCells = (int) (arm / (CELL_MM * 100));
        return of(size, size, (c, r) -> c < armCells || r < armCells);
    }

    /** L shape on any box: arms one third of the width and of the height. */
    static OccupancyMask lShape2(long w, long h) {
        int armCols = (int) Math.max(1, w / 3 / (CELL_MM * 100));
        int armRows = (int) Math.max(1, h / 3 / (CELL_MM * 100));
        return of(w, h, (c, r) -> c < armCols || r < armRows);
    }
}
