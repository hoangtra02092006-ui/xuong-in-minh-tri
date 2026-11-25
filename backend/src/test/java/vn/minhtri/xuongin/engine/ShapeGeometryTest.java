package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.file.OccupancyMask;

/** Cavity extraction (spec 8.4) and TRUE_SHAPE masks (spec 8.5). */
class ShapeGeometryTest {

    @Test
    void lShapeCavityKeepsOneGapFromTheArtwork() {
        // 108 mm L, 30 mm arms, gap 3 mm: the free corner is 78 mm, minus 3 mm next to each arm.
        List<Cavity> cavities = CavityFinder.find(Masks.lShape(10_800, 3_000), 10_800, 10_800, 300);
        assertThat(cavities).first().isEqualTo(new Cavity(3_300, 3_300, 7_500, 7_500));
        assertThat(cavities).hasSize(1);
    }

    @Test
    void solidOrMissingMaskHasNoCavity() {
        assertThat(CavityFinder.find(null, 1_000, 1_000, 0)).isEmpty();
        assertThat(CavityFinder.find(Masks.of(5_000, 5_000, (c, r) -> true), 5_000, 5_000, 0)).isEmpty();
    }

    @Test
    void cavityNarrowerThan8mmIsDropped() {
        // Empty strip 7 mm wide between two solid halves.
        OccupancyMask mask = Masks.of(10_000, 10_000, (c, r) -> c < 93 || c >= 107);
        assertThat(CavityFinder.find(mask, 10_000, 10_000, 0)).isEmpty();
    }

    @Test
    void maskWithNonWholeCellIsIgnored() {
        OccupancyMask odd = OccupancyMask.empty(10, 10, 0.333);
        assertThat(CavityFinder.cellCenti(odd)).isZero();
        assertThat(CavityFinder.find(odd, 3_330, 3_330, 0)).isEmpty();
        assertThat(ShapeRaster.baseMask(odd, 3_330, 3_330).occupiedCells()).isEqualTo(34 * 34);
    }

    @Test
    void boxPartsOutsideTheMaskGridCountAsArtwork() {
        // The grid covers only the left 10 mm of a 20 mm box.
        OccupancyMask partial = OccupancyMask.empty(20, 40, 0.5);
        BitGrid base = ShapeRaster.baseMask(partial, 2_000, 2_000);
        assertThat(base.get(5, 5)).isFalse();
        assertThat(base.get(15, 5)).isTrue();
    }

    @Test
    void upToFourCavitiesLargestFirst() {
        // A plus-shaped cross leaves four empty corners of 40 x 40 mm.
        OccupancyMask cross = Masks.of(10_000, 10_000, (c, r) -> (c >= 80 && c < 120) || (r >= 80 && r < 120));
        List<Cavity> cavities = CavityFinder.find(cross, 10_000, 10_000, 0);
        assertThat(cavities).hasSize(4).allMatch(c -> c.w() == 4_000 && c.h() == 4_000);
    }

    @Test
    void rightAngleMasksArePermutationsWithExactBoxOffsets() {
        // 10.5 x 20.3 mm solid: grid 11 x 21 cells; the real box sits flush against the rotated grid's edges.
        List<ShapeRaster.Orientation> os = ShapeRaster.orientations(null, 1_050, 2_030, true, 0);
        assertThat(os).extracting(ShapeRaster.Orientation::angleDeg).containsExactly(0, 90, 180, 270, 45, 135, 225, 315);
        ShapeRaster.Orientation r0 = os.get(0);
        ShapeRaster.Orientation r90 = os.get(1);
        ShapeRaster.Orientation r180 = os.get(2);
        ShapeRaster.Orientation r270 = os.get(3);
        assertThat(r0.mask().cols()).isEqualTo(11);
        assertThat(r0.mask().occupiedCells()).isEqualTo(11 * 21);
        assertThat(new long[] {r0.dx(), r0.dy(), r0.boxW(), r0.boxH()}).containsExactly(0, 0, 1_050, 2_030);
        assertThat(new long[] {r90.dx(), r90.dy(), r90.boxW(), r90.boxH()}).containsExactly(70, 0, 2_030, 1_050);
        assertThat(new long[] {r180.dx(), r180.dy()}).containsExactly(50, 70);
        assertThat(new long[] {r270.dx(), r270.dy()}).containsExactly(0, 50);
        assertThat(r90.mask().occupiedCells()).isEqualTo(r0.mask().occupiedCells());
    }

    @Test
    void rotationLockedGivesOnlyZeroDegrees() {
        assertThat(ShapeRaster.orientations(null, 1_000, 1_000, false, 300)).hasSize(1);
    }

    @Test
    void inflatedMaskGrowsByTheGapInCells() {
        ShapeRaster.Orientation o = ShapeRaster.orientations(null, 1_000, 1_000, false, 250).get(0);
        assertThat(o.inflated().cols()).isEqualTo(10 + 2 * 3);
        assertThat(o.inflated().occupiedCells()).isEqualTo(16 * 16);
    }

    @Test
    void diagonalMaskCoversEveryPointOfTheRotatedArtwork() {
        OccupancyMask l = Masks.lShape(5_000, 1_500);
        EngineItem item = new EngineItem("L", "L", 0, 5_000, 5_000, 1, true, l);
        for (ShapeRaster.Orientation o : ShapeRaster.orientations(l, 5_000, 5_000, true, 0)) {
            EnginePlacement at = new EnginePlacement(0, 0, o.dx(), o.dy(), o.boxW(), o.boxH(), false, o.angleDeg());
            for (double[][] quad : InvariantChecker.ink(item, at)) {
                for (int i = 0; i < 10; i++) {
                    for (int j = 0; j < 10; j++) {
                        double u = (i + 0.5) / 10.0;
                        double v = (j + 0.5) / 10.0;
                        double x = (1 - v) * ((1 - u) * quad[0][0] + u * quad[1][0]) + v * ((1 - u) * quad[3][0] + u * quad[2][0]);
                        double y = (1 - v) * ((1 - u) * quad[0][1] + u * quad[1][1]) + v * ((1 - u) * quad[3][1] + u * quad[2][1]);
                        int col = (int) Math.min(Math.floor(x / ShapeRaster.CELL), o.mask().cols() - 1);
                        int row = (int) Math.min(Math.floor(y / ShapeRaster.CELL), o.mask().rows() - 1);
                        assertThat(o.mask().get(col, row)).as("angle %d point (%.1f, %.1f)", o.angleDeg(), x, y).isTrue();
                    }
                }
            }
            assertThat(o.mask().occupiedCells()).as("angle %d keeps the corner empty", o.angleDeg())
                    .isLessThan(o.mask().cols() * o.mask().rows());
        }
    }
}
