package vn.minhtri.xuongin.export.cut;

/**
 * What one cut file holds, for the log and for checks against Cutting Master's "Job size".
 * The four registration marks sit on the page corners: (0, 0), (pageWidthMm, 0),
 * (0, pageLengthMm), (pageWidthMm, pageLengthMm).
 *
 * @param pageWidthMm   always the print sheet width
 * @param pageLengthMm  print sheet length + 2 × paddingMm
 * @param paddingMm     added at EACH end because ink crowded a corner (0 when clean)
 * @param outerContours closed cut lines around pieces
 * @param holeContours  cut lines around holes (letter counters)
 * @param droppedSpecks islands / holes below the minimum area (dust)
 */
public record CutResult(
        double pageWidthMm,
        double pageLengthMm,
        double paddingMm,
        int outerContours,
        int holeContours,
        int droppedSpecks) {

    public int contours() {
        return outerContours + holeContours;
    }
}
