package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 9 and invariant 11. Expected numbers are worked out by hand. */
class NestStatsCalculatorTest {

    @Test
    void unionCountsOverlappingBoxesOnce() {
        List<EnginePlacement> boxes = List.of(
                new EnginePlacement(0, 0, 0, 0, 100, 100, false, 0),
                new EnginePlacement(0, 1, 50, 50, 100, 100, false, 0),
                new EnginePlacement(0, 2, 60, 60, 10, 10, false, 0),
                new EnginePlacement(0, 3, 300, 0, 10, 10, false, 0));
        // 100x100 + 100x100 - 50x50 overlap; the third box is inside; the fourth is separate.
        assertThat(NestStatsCalculator.unionArea(boxes)).isEqualTo(10_000 + 10_000 - 2_500 + 100);
        assertThat(NestStatsCalculator.unionArea(List.of())).isZero();
    }

    @Test
    void nestedBoxesNeverPushFillRateAboveOne() {
        List<EngineItem> items = List.of(
                new EngineItem("big", "big", 0, 1_000, 1_000, 1, false),
                new EngineItem("small", "small", 1, 400, 400, 1, false));
        EngineParams params = new EngineParams(EngineMode.FREE, 1_000, 0, 0, null, false);
        EngineResult result = new EngineResult(List.of(new EngineSheet(1_000, 1_000, List.of(
                new EnginePlacement(0, 0, 0, 0, 1_000, 1_000, false, 0),
                new EnginePlacement(1, 1, 500, 500, 400, 400, false, 0)))));
        EngineStats stats = NestStatsCalculator.compute(items, params, result);
        assertThat(stats.fillRate()).isEqualTo(1.0);
        assertThat(stats.sheetFillRates()).containsExactly(1.0);
        assertThat(stats.coveredArea()).isEqualTo(1_000_000);
        assertThat(stats.totalShapeArea()).isEqualTo(1_160_000);
    }

    @Test
    void byTypeIsSplitByOwnAreaAndSumsExactly() {
        // Areas 2:1 -> 1000 splits 666.67 / 333.33 -> largest remainder gives 667 / 333.
        List<EngineItem> items = List.of(
                new EngineItem("a", "a", 0, 200, 100, 1, true),
                new EngineItem("b", "b", 1, 100, 100, 1, true));
        List<EngineStats.TypeLength> rows = NestStatsCalculator.splitLength(items, 1_000);
        assertThat(rows).extracting(EngineStats.TypeLength::length).containsExactly(667L, 333L);
        assertThat(rows).extracting(EngineStats.TypeLength::shapeArea).containsExactly(20_000L, 10_000L);
    }

    @Test
    void threeEqualDesignsShareTheRemainderAndStillSumExactly() {
        List<EngineItem> items = List.of(
                new EngineItem("a", "a", 0, 100, 100, 1, true),
                new EngineItem("b", "b", 1, 100, 100, 1, true),
                new EngineItem("c", "c", 2, 100, 100, 1, true));
        List<EngineStats.TypeLength> rows = NestStatsCalculator.splitLength(items, 100);
        assertThat(rows).extracting(EngineStats.TypeLength::length).containsExactly(34L, 33L, 33L);
    }

    @Test
    void thinDesignNestedInsideAnotherNeverShowsZero() {
        // The 8 x 0.9 cm design (11 prints) once showed 0 cm. Here its share rounds to 0 and must be lifted to 1.
        List<EngineItem> items = List.of(
                new EngineItem("big", "big", 0, 50_000, 50_000, 100, true),
                new EngineItem("thin", "thin-8x0.9", 1, 8_000, 900, 11, true));
        List<EngineStats.TypeLength> rows = NestStatsCalculator.splitLength(items, 1_000);
        assertThat(rows.get(1).length()).isEqualTo(1);
        assertThat(rows.stream().mapToLong(EngineStats.TypeLength::length).sum()).isEqualTo(1_000);
    }

    @Test
    void thinDesignInFreeModeGetsItsAreaShare() {
        List<EngineItem> items = List.of(
                new EngineItem("L", "L", 0, 10_800, 10_800, 1, true, Masks.lShape(10_800, 3_000)),
                new EngineItem("thin", "thin-8x0.9", 1, 8_000, 900, 11, true));
        EngineParams params = new EngineParams(EngineMode.FREE, 13_000, 500, 300, null, true);
        EngineResult result = InvariantChecker.nestAndCheck(items, params);
        EngineStats stats = NestStatsCalculator.compute(items, params, result);
        long thinArea = 8_000L * 900 * 11;
        long expected = stats.totalLength() * thinArea / stats.totalShapeArea();
        assertThat(stats.byType().get(1).length()).isBetween(expected, expected + 1).isPositive();
        assertThat(stats.byType().get(0).length() + stats.byType().get(1).length()).isEqualTo(stats.totalLength());
    }

    @Test
    void rowsAreSortedByCategoryIndex() {
        List<EngineItem> items = List.of(
                new EngineItem("b", "b", 1, 100, 100, 1, true),
                new EngineItem("a", "a", 0, 100, 100, 2, true));
        List<EngineStats.TypeLength> rows = NestStatsCalculator.splitLength(items, 300);
        assertThat(rows).extracting(EngineStats.TypeLength::itemIndex).containsExactly(1, 0);
        assertThat(rows).extracting(EngineStats.TypeLength::length).containsExactly(200L, 100L);
    }

    @Test
    void savedPercentComparesWithOneByOnePrinting() {
        // Two prints 10 x 5 cm upright, gap 0.3, margin 0.5: one by one = 1 + 2 x 5.3 = 11.6 cm.
        List<EngineItem> items = List.of(new EngineItem("a", "a", 0, 10_000, 5_000, 2, true));
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        EngineResult result = new EngineResult(List.of(new EngineSheet(57_000, 5_800, List.of(
                new EnginePlacement(0, 0, 500, 500, 10_000, 5_000, false, 0),
                new EnginePlacement(0, 1, 10_800, 500, 10_000, 5_000, false, 0)))));
        EngineStats stats = NestStatsCalculator.compute(items, params, result);
        assertThat(stats.savedPct()).isCloseTo((1 - 5_800 / 11_600.0) * 100, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(stats.usedArea()).isEqualTo(57_000L * 5_800);
        assertThat(stats.totalPieces()).isEqualTo(2);
    }

    @Test
    void savedPercentNeverNegative() {
        List<EngineItem> items = List.of(new EngineItem("a", "a", 0, 100, 100, 1, true));
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 1_000, 0, 0, null, true);
        EngineResult result = new EngineResult(List.of(new EngineSheet(1_000, 5_000, List.of(
                new EnginePlacement(0, 0, 0, 0, 100, 100, false, 0)))));
        assertThat(NestStatsCalculator.compute(items, params, result).savedPct()).isZero();
    }
}
