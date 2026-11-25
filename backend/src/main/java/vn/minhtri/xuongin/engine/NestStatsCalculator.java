package vn.minhtri.xuongin.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Pure computation of the result figures (spec 9) from an engine result. */
public final class NestStatsCalculator {

    private NestStatsCalculator() {}

    public static EngineStats compute(List<EngineItem> items, EngineParams params, EngineResult result) {
        long totalLength = 0;
        long usedArea = 0;
        long covered = 0;
        int pieces = 0;
        List<Double> sheetFill = new ArrayList<>(result.sheets().size());
        for (EngineSheet sheet : result.sheets()) {
            long union = unionArea(sheet.placements());
            long area = sheet.width() * sheet.length();
            sheetFill.add(area > 0 ? (double) union / area : 0);
            totalLength += sheet.length();
            usedArea += area;
            covered += union;
            pieces += sheet.placements().size();
        }

        long totalShapeArea = 0;
        long individual = 2 * params.margin();
        for (EngineItem item : items) {
            totalShapeArea += item.w() * item.h() * item.quantity();
            // Printed one by one: each print takes the full width, upright, one gap apart.
            individual += (item.h() + params.gap()) * item.quantity();
        }
        double saved = individual > 0 ? Math.max(0, (individual - totalLength) * 100.0 / individual) : 0;

        return new EngineStats(result.sheets().size(), totalLength, totalShapeArea, usedArea, covered,
                usedArea > 0 ? (double) covered / usedArea : 0, saved, pieces, sheetFill,
                splitLength(items, totalLength));
    }

    /**
     * Splits the total length by each design's own print area (never by the paper it alone occupies:
     * a thin design nested inside another's box would show 0 and its customer would get paper for free).
     * Largest remainder keeps the sum exact; a design that would round to 0 takes 1/100 mm from the largest.
     */
    static List<EngineStats.TypeLength> splitLength(List<EngineItem> items, long totalLength) {
        int n = items.size();
        long[] areas = new long[n];
        long totalArea = 0;
        for (int i = 0; i < n; i++) {
            EngineItem item = items.get(i);
            areas[i] = item.w() * item.h() * item.quantity();
            totalArea += areas[i];
        }
        long[] lengths = new long[n];
        BigInteger[] remainders = new BigInteger[n];
        BigInteger total = BigInteger.valueOf(totalLength);
        BigInteger divisor = BigInteger.valueOf(Math.max(1, totalArea));
        long assigned = 0;
        for (int i = 0; i < n; i++) {
            BigInteger[] qr = total.multiply(BigInteger.valueOf(areas[i])).divideAndRemainder(divisor);
            lengths[i] = qr[0].longValueExact();
            remainders[i] = qr[1];
            assigned += lengths[i];
        }
        Integer[] byRemainder = new Integer[n];
        Arrays.setAll(byRemainder, i -> i);
        Arrays.sort(byRemainder, Comparator.comparing((Integer i) -> remainders[i]).reversed()
                .thenComparingInt(i -> i));
        for (int k = 0; k < totalLength - assigned; k++) {
            lengths[byRemainder[k % n]]++;
        }
        for (int i = 0; i < n; i++) {
            if (lengths[i] == 0) {
                int donor = largest(lengths);
                if (lengths[donor] > 1) {
                    lengths[donor]--;
                    lengths[i]++;
                }
            }
        }

        List<EngineStats.TypeLength> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new EngineStats.TypeLength(i, items.get(i).quantity(), areas[i], lengths[i]));
        }
        rows.sort(Comparator.comparingInt((EngineStats.TypeLength r) -> items.get(r.itemIndex()).categoryIndex())
                .thenComparingInt(EngineStats.TypeLength::itemIndex));
        return rows;
    }

    private static int largest(long[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    /**
     * Exact area of the union of the bounding boxes by coordinate compression: for each x-slab, merge the
     * y-intervals of the boxes spanning it. Boxes overlap in FREE / TRUE_SHAPE; summing them would exceed 100 %.
     */
    public static long unionArea(List<EnginePlacement> boxes) {
        if (boxes.isEmpty()) {
            return 0;
        }
        long[] xs = boxes.stream().flatMapToLong(b -> java.util.stream.LongStream.of(b.x(), b.right()))
                .sorted().distinct().toArray();
        List<EnginePlacement> byY = new ArrayList<>(boxes);
        byY.sort(Comparator.comparingLong(EnginePlacement::y));
        long area = 0;
        for (int i = 0; i + 1 < xs.length; i++) {
            long x0 = xs[i];
            long x1 = xs[i + 1];
            long covered = 0;
            long runStart = Long.MIN_VALUE;
            long runEnd = Long.MIN_VALUE;
            for (EnginePlacement b : byY) {
                if (b.x() > x0 || b.right() < x1) {
                    continue;
                }
                if (b.y() > runEnd) {
                    covered += runEnd - runStart;
                    runStart = b.y();
                    runEnd = b.top();
                } else {
                    runEnd = Math.max(runEnd, b.top());
                }
            }
            covered += runEnd - runStart;
            area += covered * (x1 - x0);
        }
        return area;
    }
}
