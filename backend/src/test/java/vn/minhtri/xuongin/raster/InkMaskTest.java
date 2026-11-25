package vn.minhtri.xuongin.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.export.pdf.SheetFixtures;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;

/** Spec 10.3: alpha decides ink; background flood only for opaque images. */
class InkMaskTest {

    private static final int DPI = 300;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLUE = 0xFF2040C0;

    @TempDir
    Path tmp;

    @Test
    void whiteTextInAPdfGetsInk() throws IOException {
        SheetFixtures fx = new SheetFixtures(tmp);
        fx.pdf("logo", fx.whiteArtworkPdf("white", 60, 20), 60, 20);
        Placement p = fx.place("logo", 10, 10, false);
        Sheet sheet = fx.sheet(80, 40, List.of(p));
        Path pdf = fx.compose("sheet", 80, 40, false, sheet.placements());

        Built built = build(fx, sheet, pdf);
        assertThat(built.zones).isEmpty();
        // White bar: points (4..166 pt, 4..10 pt) of the source, i.e. centre ~ (10 + 30 mm, 10 + 2.47 mm).
        assertThat(built.at(10 + 30, 10 + 2.47)).isEqualTo(InkMask.INK);
        // Text row has ink pixels; empty sheet corner has none.
        assertThat(built.countInk(10, 10 + 10, 70, 10 + 19)).isGreaterThan(500);
        assertThat(built.at(78, 38)).isEqualTo(InkMask.NONE);
    }

    @Test
    void jpgWhiteBackgroundIsNotInkButEnclosedWhiteIs() throws IOException {
        SheetFixtures fx = new SheetFixtures(tmp);
        Path jpg = fx.imageFile("logo.jpg", logo());
        fx.image("jpg", jpg, 25.4, 25.4, false);
        // Same picture declared with alpha (e.g. PNG with transparency): its white is deliberate.
        fx.image("alpha", jpg, 25.4, 25.4, true);
        Sheet sheet = fx.sheet(80, 40, List.of(fx.place("jpg", 5, 5, false), fx.place("alpha", 45, 5, false)));
        Path pdf = fx.compose("sheet", 80, 40, false, sheet.placements());

        Built built = build(fx, sheet, pdf);
        assertThat(built.zones).hasSize(1);
        // Image 200 px over 25.4 mm: 1 px = 0.127 mm. Background corner, blue body, white dot centre.
        assertThat(built.at(5 + 2, 5 + 2)).isEqualTo(InkMask.NONE);
        assertThat(built.at(5 + 8, 5 + 8)).isEqualTo(InkMask.INK);
        assertThat(built.at(5 + 12.7, 5 + 12.7)).isEqualTo(InkMask.INK);
        // Declared alpha: no flood, the white background keeps ink.
        assertThat(built.at(45 + 2, 5 + 2)).isEqualTo(InkMask.INK);
        assertThat(built.at(45 + 12.7, 5 + 12.7)).isEqualTo(InkMask.INK);
    }

    @Test
    void alphaThresholdIsStrict() {
        InkMask mask = new InkMask(3, 1, 128, 6);
        mask.accept(0, 1, 3, new int[] {0x80FF0000, 0x81FF0000, 0});
        assertThat(mask.finish(null)).containsExactly(InkMask.NONE, InkMask.INK, InkMask.NONE);
    }

    @Test
    void floodStaysInsideZonesAndStopsAtColour() {
        // Row-major 7 x 3. Zone covers x 0..4. Pale run from the zone edge to x = 2 stops at blue;
        // pale outside the zone (x 5..6) is never flooded.
        int w = 7;
        int[] px = {
            WHITE, WHITE, WHITE, BLUE, BLUE, WHITE, WHITE,
            BLUE, BLUE, BLUE, BLUE, BLUE, WHITE, WHITE,
            BLUE, WHITE, BLUE, BLUE, BLUE, WHITE, WHITE,
        };
        InkMask mask = new InkMask(w, 3, 128, 6);
        mask.accept(0, 3, w, px);
        byte[] m = mask.finish(List.of(new PixelRect(0, 0, 5, 3)));
        // Row 0: flooded pale -> none; blue stays.
        assertThat(slice(m, 0, w)).containsExactly(0, 0, 0, 1, 1, 1, 1);
        // Row 2, x = 1: pale on the zone's bottom edge -> seed -> none. Outside the zone, white stays ink.
        assertThat(slice(m, 2 * w, w)).containsExactly(1, 0, 1, 1, 1, 1, 1);
    }

    @Test
    void transparentNeighbourSeedsTheFloodAndEnclosedWhiteSurvives() {
        int w = 7;
        int c = 0;
        int[] px = {
            c, c, c, c, c, c, c,
            c, WHITE, BLUE, BLUE, BLUE, WHITE, c,
            c, WHITE, BLUE, WHITE, BLUE, WHITE, c,
            c, WHITE, BLUE, BLUE, BLUE, WHITE, c,
            c, c, c, c, c, c, c,
        };
        InkMask mask = new InkMask(w, 5, 128, 6);
        mask.accept(0, 5, w, px);
        byte[] m = mask.finish(List.of(new PixelRect(-3, -3, 30, 30)));
        assertThat(slice(m, 2 * w, w)).containsExactly(0, 0, 1, 1, 1, 0, 0);
    }

    @Test
    void bandsCanArriveInAnySizeAndMisfitsAreRejected() {
        InkMask mask = new InkMask(2, 3, 128, 6);
        mask.accept(0, 2, 2, new int[] {BLUE, 0, 0, BLUE});
        mask.accept(2, 1, 2, new int[] {BLUE, BLUE});
        assertThatThrownBy(() -> mask.accept(2, 2, 2, new int[4])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mask.accept(0, 1, 3, new int[3])).isInstanceOf(IllegalArgumentException.class);
        assertThat(mask.finish(List.of())).containsExactly(1, 0, 0, 1, 1, 1);
        assertThatThrownBy(() -> mask.finish(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InkMask(0, 1, 128, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mask.width()).isEqualTo(2);
        assertThat(mask.height()).isEqualTo(3);
    }

    @Test
    void erodeAndDilateUseASquareKernelAndTheSheetEdgeIsEmpty() {
        int w = 9;
        byte[] square = new byte[w * w];
        for (int y = 2; y < 7; y++) {
            for (int x = 2; x < 7; x++) {
                square[y * w + x] = InkMask.INK;
            }
        }
        assertThat(count(InkMask.erode(square, w, w, 1))).isEqualTo(9);
        assertThat(count(InkMask.erode(square, w, w, 3))).isZero();
        assertThat(count(InkMask.dilate(square, w, w, 1))).isEqualTo(49);
        assertThat(count(InkMask.dilate(square, w, w, 5))).isEqualTo(81);
        assertThat(InkMask.erode(square, w, w, 0)).isEqualTo(square).isNotSameAs(square);
        assertThat(InkMask.dilate(square, w, w, 0)).isEqualTo(square);

        byte[] full = new byte[w * w];
        java.util.Arrays.fill(full, InkMask.INK);
        // Ink touching the sheet edge shrinks too (7 x 7 left).
        assertThat(count(InkMask.erode(full, w, w, 1))).isEqualTo(49);
    }

    private Built build(SheetFixtures fx, Sheet sheet, Path pdf) throws IOException {
        RasterProperties props = new RasterProperties(null, null, 64, null, null);
        RasterSize size = SheetRasterizer.sizeOf(sheet.widthMm(), sheet.lengthMm(), DPI);
        List<PixelRect> zones = PdfComposer.opaqueImageZones(sheet, fx::get, DPI, size.height());
        byte[] mask = InkMask.build(new SheetRasterizer(props), pdf, DPI, props,
                sheet.widthMm(), sheet.lengthMm(), zones);
        assertThat(mask).hasSize((int) size.pixels());
        return new Built(mask, size, zones);
    }

    /** 200 x 200 px: white background, blue square 40..160, white dot 80..120 inside it. */
    private static BufferedImage logo() {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.setColor(new Color(BLUE));
        g.fillRect(40, 40, 120, 120);
        g.setColor(Color.WHITE);
        g.fillRect(80, 80, 40, 40);
        g.dispose();
        return img;
    }

    private static int[] slice(byte[] m, int from, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = m[from + i];
        }
        return out;
    }

    private static int count(byte[] m) {
        int n = 0;
        for (byte b : m) {
            n += b == InkMask.INK ? 1 : 0;
        }
        return n;
    }

    private record Built(byte[] mask, RasterSize size, List<PixelRect> zones) {
        int px(double mm) {
            return (int) Math.floor(mm * DPI / 25.4);
        }

        byte at(double xMm, double yMm) {
            return mask[(size.height() - 1 - px(yMm)) * size.width() + px(xMm)];
        }

        int countInk(double x0, double y0, double x1, double y1) {
            int n = 0;
            for (int y = size.height() - px(y1); y < size.height() - px(y0); y++) {
                for (int x = px(x0); x < px(x1); x++) {
                    n += mask[y * size.width() + x] == InkMask.INK ? 1 : 0;
                }
            }
            return n;
        }
    }
}
