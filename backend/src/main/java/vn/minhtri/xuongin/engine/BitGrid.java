package vn.minhtri.xuongin.engine;

/**
 * Bit grid of cells (row 0 at the bottom). Each row is packed into {@code long} words so a collision test
 * is one AND per 64 cells (spec 8.5).
 */
final class BitGrid {

    private final int cols;
    private final int rows;
    private final int wordsPerRow;
    private final long[] bits;

    BitGrid(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.wordsPerRow = (cols + 63) >>> 6;
        this.bits = new long[wordsPerRow * rows];
    }

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    int wordsPerRow() {
        return wordsPerRow;
    }

    long[] bits() {
        return bits;
    }

    boolean get(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return false;
        }
        return (bits[row * wordsPerRow + (col >>> 6)] & (1L << (col & 63))) != 0;
    }

    void set(int col, int row) {
        if (col >= 0 && row >= 0 && col < cols && row < rows) {
            bits[row * wordsPerRow + (col >>> 6)] |= 1L << (col & 63);
        }
    }

    int occupiedCells() {
        int count = 0;
        for (long word : bits) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /** Exact counter-clockwise turn by 0, 90, 180 or 270 degrees: a pure index permutation, no resampling. */
    BitGrid rotatedRightAngle(int degrees) {
        if (degrees == 0) {
            return this;
        }
        boolean swap = degrees == 90 || degrees == 270;
        BitGrid turned = new BitGrid(swap ? rows : cols, swap ? cols : rows);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!get(c, r)) {
                    continue;
                }
                switch (degrees) {
                    case 90 -> turned.set(rows - 1 - r, c);
                    case 180 -> turned.set(cols - 1 - c, rows - 1 - r);
                    case 270 -> turned.set(r, cols - 1 - c);
                    default -> throw new IllegalArgumentException("Not a right angle: " + degrees);
                }
            }
        }
        return turned;
    }

    /** Grows every set cell by {@code radius} cells in all directions; the grid grows by radius on each side. */
    BitGrid dilatedExpanding(int radius) {
        if (radius <= 0) {
            return this;
        }
        BitGrid grown = new BitGrid(cols + 2 * radius, rows + 2 * radius);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!get(c, r)) {
                    continue;
                }
                for (int rr = r; rr <= r + 2 * radius; rr++) {
                    for (int cc = c; cc <= c + 2 * radius; cc++) {
                        grown.set(cc, rr);
                    }
                }
            }
        }
        return grown;
    }

    /** Lowest set row of each column, -1 for an empty column. */
    int[] bottomProfile() {
        int[] profile = new int[cols];
        for (int c = 0; c < cols; c++) {
            profile[c] = -1;
            for (int r = 0; r < rows; r++) {
                if (get(c, r)) {
                    profile[c] = r;
                    break;
                }
            }
        }
        return profile;
    }
}
