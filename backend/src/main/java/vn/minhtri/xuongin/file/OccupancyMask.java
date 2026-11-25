package vn.minhtri.xuongin.file;

/**
 * Bitmap of which cells of the content box contain artwork (cell size {@code cellMm}, default 0.5 mm).
 * Row 0 is the bottom row. Used by FREE and TRUE_SHAPE nesting. Images have no mask (treated as solid).
 */
public record OccupancyMask(int cols, int rows, double cellMm, long[] bits) {

    public static final double DEFAULT_CELL_MM = 0.5;

    /** Out-of-range cells count as occupied (safe side: never place artwork there). */
    public boolean isOccupied(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return true;
        }
        int index = row * cols + col;
        return (bits[index >>> 6] & (1L << (index & 63))) != 0;
    }

    public static OccupancyMask empty(int cols, int rows, double cellMm) {
        return new OccupancyMask(cols, rows, cellMm, new long[(cols * rows + 63) >>> 6]);
    }

    public void set(int col, int row) {
        int index = row * cols + col;
        bits[index >>> 6] |= 1L << (index & 63);
    }
}
