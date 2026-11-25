package vn.minhtri.xuongin.export.tif;

/**
 * Computes one row of the W1 mask: the ink mask contracted by {@code radius} pixels on every
 * side (square neighbourhood, like Photoshop "Select, Modify, Contract"), so the white sits inside
 * the colour (spec 10.3). Pixels outside the sheet count as no ink, so shapes touching the edge
 * are contracted too. Works row by row on the whole-sheet mask: no second mask is allocated.
 */
final class WhiteChoke {

    private final byte[] mask;
    private final int width;
    private final int height;
    private final int radius;
    private final boolean[] column;

    WhiteChoke(byte[] mask, int width, int height, int radius) {
        this.mask = mask;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.column = new boolean[width];
    }

    /** Fills {@code out[0, width)} with true where row {@code y} of W1 has white ink. */
    void row(int y, boolean[] out) {
        int base = y * width;
        if (radius == 0) {
            for (int x = 0; x < width; x++) {
                out[x] = mask[base + x] != 0;
            }
            return;
        }
        if (y - radius < 0 || y + radius >= height) {
            java.util.Arrays.fill(out, 0, width, false);
            return;
        }
        for (int x = 0; x < width; x++) {
            boolean ink = true;
            for (int at = (y - radius) * width + x, end = (y + radius) * width + x; at <= end; at += width) {
                if (mask[at] == 0) {
                    ink = false;
                    break;
                }
            }
            column[x] = ink;
        }
        // Sliding window: out[x] is ink when no gap lies in column[x - r .. x + r].
        int gaps = 0;
        for (int x = -radius; x <= radius; x++) {
            if (x < 0 || x >= width || !column[x]) {
                gaps++;
            }
        }
        for (int x = 0; x < width; x++) {
            out[x] = gaps == 0;
            int leaving = x - radius;
            int entering = x + radius + 1;
            if (leaving < 0 || !column[leaving]) {
                gaps--;
            }
            if (entering >= width || !column[entering]) {
                gaps++;
            }
        }
    }
}
