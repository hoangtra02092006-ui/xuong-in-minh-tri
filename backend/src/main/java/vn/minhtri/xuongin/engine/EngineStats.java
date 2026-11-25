package vn.minhtri.xuongin.engine;

import java.util.List;

/**
 * Totals of spec 9, in engine units (lengths 1/100 mm, areas (1/100 mm)^2). Ratios are unrounded.
 *
 * @param totalSheets     number of sheets
 * @param totalLength     sum of sheet lengths
 * @param totalShapeArea  sum of w x h x quantity (real size, no gap)
 * @param usedArea        sum of width x length of the sheets
 * @param coveredArea     union of all bounding boxes (each sheet separately), never counted twice
 * @param fillRate        coveredArea / usedArea, never above 1
 * @param savedPct        (1 - totalLength / length when printed one by one) x 100, never below 0
 * @param totalPieces     number of prints
 * @param sheetFillRates  per sheet: union of its boxes / (width x length)
 * @param byType          one row per input item, sorted by categoryIndex then input order
 */
public record EngineStats(
        int totalSheets,
        long totalLength,
        long totalShapeArea,
        long usedArea,
        long coveredArea,
        double fillRate,
        double savedPct,
        int totalPieces,
        List<Double> sheetFillRates,
        List<TypeLength> byType) {

    public EngineStats {
        sheetFillRates = List.copyOf(sheetFillRates);
        byType = List.copyOf(byType);
    }

    /**
     * Paper length charged to one design: its share of the total by its own print area (spec 9).
     *
     * @param itemIndex index in the input list
     * @param pieces    number of prints
     * @param shapeArea w x h x pieces
     * @param length    1/100 mm; the rows add up exactly to totalLength and none is 0
     */
    public record TypeLength(int itemIndex, int pieces, long shapeArea, long length) {}
}
