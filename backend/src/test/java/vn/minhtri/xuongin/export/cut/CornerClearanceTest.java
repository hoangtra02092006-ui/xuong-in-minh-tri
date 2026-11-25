package vn.minhtri.xuongin.export.cut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.export.cut.CutProperties.Corner;

/** 254 DPI: exactly 0.1 mm per pixel, so every distance is checked by hand. */
class CornerClearanceTest {

    private static final int DPI = 254;
    private static final int W = 1000; // 100 mm
    private static final int H = 600; // 60 mm
    private static final double ZONE = 20;

    private static byte[] withInk(int x, int y) {
        byte[] mask = new byte[W * H];
        mask[y * W + x] = 1;
        return mask;
    }

    @Test
    void cleanSheetNeedsNoPadding() {
        assertThat(CornerClearance.paddingMm(new byte[W * H], W, H, 100, 60, DPI, ZONE)).isZero();
    }

    @Test
    void inkInTheBottomLeftCornerNeedsTheMissingDistance() {
        // Pixel row 499 spans 10.0..10.1 mm from the bottom → clear 10.0 → pad 10.
        byte[] mask = withInk(5, 499);
        assertThat(CornerClearance.clearanceMm(mask, W, H, 100, 60, 0.1, ZONE, Corner.BOTTOM_LEFT))
                .isCloseTo(10.0, within(1e-9));
        assertThat(CornerClearance.paddingMm(mask, W, H, 100, 60, DPI, ZONE)).isEqualTo(10);
    }

    @Test
    void paddingRoundsUpToWholeMm() {
        // Row 52 starts 5.2 mm below the top → needs 14.8 → 15.
        byte[] mask = withInk(W - 1, 52);
        assertThat(CornerClearance.paddingMm(mask, W, H, 100, 60, DPI, ZONE)).isEqualTo(15);
    }

    @Test
    void firstInkRowFromTheEdgeDecidesNotTheLast() {
        // Two rows of ink in the top-left corner: 3 mm and 15 mm from the top. The nearer one
        // decides (a plain break of the inner loop would report the farther row).
        byte[] mask = withInk(10, 30);
        mask[150 * W + 10] = 1;
        assertThat(CornerClearance.paddingMm(mask, W, H, 100, 60, DPI, ZONE)).isEqualTo(17);
    }

    @Test
    void inkOutsideTheCornerStripsIsIgnored() {
        // Touches the bottom edge at x = 50 mm: outside both 20 mm column strips.
        assertThat(CornerClearance.paddingMm(withInk(500, H - 1), W, H, 100, 60, DPI, ZONE)).isZero();
    }

    @Test
    void rightStripStartsAtTheLastZoneOfTheWidth() {
        // Column 799 spans 79.9..80.0 mm: outside [80, 100]; column 800 is inside.
        assertThat(CornerClearance.paddingMm(withInk(799, H - 1), W, H, 100, 60, DPI, ZONE)).isZero();
        assertThat(CornerClearance.paddingMm(withInk(800, H - 1), W, H, 100, 60, DPI, ZONE)).isEqualTo(20);
    }

    @Test
    void neverMoreThanTheZone() {
        assertThat(CornerClearance.paddingMm(withInk(0, 0), W, H, 100, 60, DPI, 20.5)).isEqualTo(20.5);
    }

    @Test
    void shortSheetWithoutInkIsClean() {
        assertThat(CornerClearance.clearanceMm(new byte[W * 10], W, 10, 100, 1, 0.1, ZONE, Corner.TOP_RIGHT))
                .isEqualTo(ZONE);
    }
}
