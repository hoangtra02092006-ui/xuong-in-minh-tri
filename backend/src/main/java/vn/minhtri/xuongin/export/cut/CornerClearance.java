package vn.minhtri.xuongin.export.cut;

/**
 * How much to lengthen the cut page at each end so the four registration-mark corners are
 * clean (spec 10.5 "Góc chật thì nới trang DÀI ra").
 *
 * <p>Measured on the real ink of the cut mask, not on bounding boxes, and in millimetres; the
 * zone is converted to pixels only to choose which pixels to look at. Lengthening by {@code p}
 * at both ends moves every pixel {@code p} further from the top and bottom edges, so
 * {@code p = zone} always clears every corner: the result never exceeds the zone.
 * Never widened: the roll is 603 mm and Cutting Master accepts at most 576 mm.
 */
final class CornerClearance {

    private static final int INK_BIT = 1;

    private CornerClearance() {}

    /**
     * @param mask     cut mask (bit 0 = ink), row 0 = top of the sheet
     * @param widthMm  sheet width (from the sheet data, not from the pixel count)
     * @param lengthMm sheet length
     * @param dpi      raster DPI of the mask
     * @param zoneMm   clean square needed at each corner (mark + clearance)
     * @return mm added at EACH end: 0, or a whole number of mm, at most {@code zoneMm}
     */
    static double paddingMm(byte[] mask, int width, int height, double widthMm, double lengthMm, int dpi,
            double zoneMm) {
        double mmPerPx = 25.4 / dpi;
        double needed = 0;
        for (CutProperties.Corner corner : CutProperties.Corner.values()) {
            double clear = clearanceMm(mask, width, height, widthMm, lengthMm, mmPerPx, zoneMm, corner);
            needed = Math.max(needed, zoneMm - clear);
        }
        return needed <= 0 ? 0 : Math.min(Math.ceil(needed), zoneMm);
    }

    /**
     * Distance from the page's top / bottom edge to the nearest ink inside the corner's
     * column strip; {@code zoneMm} or more when the corner square is clean.
     */
    static double clearanceMm(byte[] mask, int width, int height, double widthMm, double lengthMm,
            double mmPerPx, double zoneMm, CutProperties.Corner corner) {
        int x0;
        int x1;
        if (corner.right()) {
            // Columns whose right edge reaches into [width - zone, width].
            x0 = Math.max(0, (int) Math.floor((widthMm - zoneMm) / mmPerPx));
            x1 = width;
        } else {
            x0 = 0;
            x1 = Math.min(width, (int) Math.ceil(zoneMm / mmPerPx));
        }
        // Rows scanned from the corner's edge inwards; the first row with ink decides, so
        // return straight out of BOTH loops (a plain break would keep the last row instead).
        for (int step = 0; step < height; step++) {
            int row = corner.top() ? step : height - 1 - step;
            double distance = corner.top() ? row * mmPerPx : lengthMm - (row + 1) * mmPerPx;
            if (distance >= zoneMm) {
                return zoneMm;
            }
            int offset = row * width;
            for (int x = x0; x < x1; x++) {
                if ((mask[offset + x] & INK_BIT) != 0) {
                    return Math.max(0, distance);
                }
            }
        }
        return zoneMm;
    }
}
