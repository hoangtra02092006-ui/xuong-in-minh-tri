package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.file.OccupancyMask;

/**
 * Spec 15: an L shape (like samples/thu-chu-L-108.pdf) must take the small pieces into its empty corner in
 * FREE and TRUE_SHAPE, with real ink never closer than the gap. The roll is too narrow to put them beside it.
 */
class LShapeTest {

    private static final long L_SIZE = 10_800;
    private static final OccupancyMask L_MASK = Masks.lShape(L_SIZE, 3_000);

    private static List<EngineItem> order() {
        return List.of(
                new EngineItem("L", "thu-chu-L-108.pdf", 0, L_SIZE, L_SIZE, 1, true, L_MASK),
                new EngineItem("s", "nho-3x3.pdf", 1, 3_000, 3_000, 4, true));
    }

    private static EngineParams params(EngineMode mode) {
        return new EngineParams(mode, 12_000 + 1_000, 500, 300, null, true);
    }

    @Test
    void freeModePutsSmallPiecesInsideTheCavity() {
        assertSmallPiecesNestedInL(EngineMode.FREE);
    }

    @Test
    void trueShapeModePutsSmallPiecesInsideTheCavity() {
        assertSmallPiecesNestedInL(EngineMode.TRUE_SHAPE);
    }

    @Test
    void orthogonalModeKeepsTheBoxSolidAndIsLonger() {
        EngineResult orthogonal = InvariantChecker.nestAndCheck(order(), params(EngineMode.ORTHOGONAL));
        EngineResult free = InvariantChecker.nestAndCheck(order(), params(EngineMode.FREE));
        assertThat(insideL(orthogonal)).isZero();
        assertThat(free.totalLength()).isLessThan(orthogonal.totalLength());
    }

    @Test
    void cavityFollowsTheHostWhenItIsRotated() {
        // Rotation locked for the small pieces only, so they must use the L's cavity in whatever way it turns.
        List<EngineItem> items = List.of(
                new EngineItem("L", "L.pdf", 0, L_SIZE, L_SIZE, 2, true, L_MASK),
                new EngineItem("s", "s.pdf", 1, 3_000, 3_000, 8, false));
        EngineResult result = InvariantChecker.nestAndCheck(items, params(EngineMode.FREE));
        assertThat(insideL(result)).isEqualTo(8);
    }

    private static void assertSmallPiecesNestedInL(EngineMode mode) {
        EngineResult result = InvariantChecker.nestAndCheck(order(), params(mode));
        assertThat(result.sheets()).hasSize(1);
        assertThat(insideL(result)).as("small pieces whose box lies inside the L's box").isEqualTo(4);
        EngineSheet sheet = result.sheets().get(0);
        assertThat(sheet.length()).as("nothing above the L").isEqualTo(L_SIZE + 2 * 500);
        EngineStats stats = NestStatsCalculator.compute(order(), params(mode), result);
        assertThat(stats.fillRate()).isLessThanOrEqualTo(1.0);
        assertThat(stats.coveredArea()).isEqualTo(L_SIZE * L_SIZE);
    }

    /** Number of small pieces whose box lies inside some L's box. */
    private static long insideL(EngineResult result) {
        long count = 0;
        for (EngineSheet sheet : result.sheets()) {
            List<EnginePlacement> hosts = sheet.placements().stream().filter(p -> p.itemIndex() == 0).toList();
            count += sheet.placements().stream()
                    .filter(p -> p.itemIndex() == 1)
                    .filter(p -> hosts.stream().anyMatch(h -> p.x() >= h.x() && p.right() <= h.right()
                            && p.y() >= h.y() && p.top() <= h.top()))
                    .count();
        }
        return count;
    }
}
