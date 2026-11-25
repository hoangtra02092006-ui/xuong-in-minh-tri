package vn.minhtri.xuongin.export.cut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.minhtri.xuongin.export.cut.CutProperties.MarkShape;
import vn.minhtri.xuongin.export.pdf.SheetFixtures;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.RasterMemoryGuard;
import vn.minhtri.xuongin.raster.RasterProperties;
import vn.minhtri.xuongin.raster.SheetRasterizer;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.shared.Units;

/** Spec 10.5: cut file read back from the PDF bytes. */
class CutComposerTest {

    /** One pixel at 300 DPI is 0.085 mm; the traced edge lies on pixel corners. */
    private static final double PX_MM = 25.4 / 300;

    private static final long TWO_GB = 2L << 30;

    /** Ships inside the PDFBox jar, so every OS renders the same glyphs. */
    private static final String EMBEDDED_FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    @TempDir
    Path tmp;

    private SheetFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new SheetFixtures(tmp);
    }

    // ---------- samples and contours ----------

    @Test
    void lShapeSampleGivesOneOuterContourOnTheArtworkEdge() throws IOException {
        // thu-chu-L-108.pdf: an L of 108 x 108 mm (306.14 pt) at (113.39, 170.08) pt of an A4 page.
        fx.pdf("L", SheetFixtures.SAMPLES.resolve("thu-chu-L-108.pdf"),
                new ContentBox(113.39, 170.08, 306.14, 305.82), 0, 210, 297);
        Placement piece = fx.place("L", 50, 40, false);
        Result cut = compose(570, 300, false, List.of(piece), CutProperties.defaults());

        assertThat(cut.result.outerContours()).isEqualTo(1);
        assertThat(cut.result.holeContours()).isZero();
        assertThat(cut.pdf.cutPaths()).hasSize(1);
        // An L has exactly 6 corners; the stair-free edges simplify to them.
        assertThat(cut.pdf.cutPaths().get(0).points()).hasSize(6);
        double[] b = cut.pdf.cutPaths().get(0).bounds();
        assertThat(b[0]).isCloseTo(50, within(PX_MM + 0.01));
        assertThat(b[1]).isCloseTo(40, within(PX_MM + 0.01));
        assertThat(b[2]).isCloseTo(50 + piece.wMm(), within(PX_MM + 0.01));
        assertThat(b[3]).isCloseTo(40 + piece.hMm(), within(PX_MM + 0.01));
    }

    @Test
    void pageWidthIsThePrintWidthAndLayersAndSpotFollowTheSpec() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        Result cut = compose(570.37, 200, false,
                List.of(fx.place("a", 100, 50, false), fx.place("a", 300, 120, true)), CutProperties.defaults());

        // Width from the sheet data, never back from the pixel count (6736.4 px floored).
        assertThat(cut.pdf.widthMm).isCloseTo(570.37, within(0.001));
        assertThat(cut.pdf.lengthMm).isCloseTo(200, within(0.001));
        assertThat(cut.result.paddingMm()).isZero();
        assertThat(cut.pdf.layers).containsExactly("CutContour", "RegMarks");
        assertThat(cut.pdf.separationName).isEqualTo("CutContour");
        assertThat(cut.pdf.separationAlternate).isEqualTo("DeviceCMYK");
        assertThat(cut.pdf.cutPaths()).hasSize(2).allSatisfy(path ->
                assertThat(path.strokeColourSpace()).isEqualTo("Separation"));
        assertThat(cut.pdf.strokedOutsideCutLayer).isEmpty();
        assertThat(cut.pdf.filledRects).allSatisfy(r -> assertThat(r.layer()).isEqualTo("RegMarks"));
    }

    @Test
    void spotNameAndStrokeComeFromConfiguration() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        CutProperties props = new CutProperties(null, null, null, null, 0.5, "Thru-cut",
                null, null, null, null, null, null, null, null);
        Result cut = compose(300, 200, false, List.of(fx.place("a", 100, 50, false)), props);
        assertThat(cut.pdf.separationName).isEqualTo("Thru-cut");
        assertThat(cut.pdf.layers).contains("CutContour");
    }

    @Test
    void ringGetsAnOuterAndAHoleContour() throws IOException {
        Path ring = ringPdf("ring", 30, 10);
        fx.pdf("ring", ring, 30, 30);
        Result cut = compose(300, 200, false, List.of(fx.place("ring", 100, 80, false)), CutProperties.defaults());

        assertThat(cut.result.outerContours()).isEqualTo(1);
        assertThat(cut.result.holeContours()).isEqualTo(1);
        assertThat(cut.pdf.cutPaths()).hasSize(2);
        double[] hole = cut.pdf.cutPaths().stream().map(CutPdf.Path::bounds)
                .filter(b -> b[2] - b[0] < 20).findFirst().orElseThrow();
        assertThat(hole[0]).isCloseTo(110, within(PX_MM + 0.01));
        assertThat(hole[2] - hole[0]).isCloseTo(10, within(2 * PX_MM + 0.01));
    }

    @Test
    void letterOHasItsCounterCutOut() throws IOException {
        fx.pdf("o", textPdf("o", 40, 40, 96, "O"), 40, 40);
        Result cut = compose(300, 200, false, List.of(fx.place("o", 60, 60, false)), CutProperties.defaults());
        assertThat(cut.result.outerContours()).isEqualTo(1);
        assertThat(cut.result.holeContours()).isEqualTo(1);
    }

    @Test
    void smallTextKeepsEveryLetter() throws IOException {
        // 7 pt caps (cap height ~1.8 mm, strokes ~0.2 mm = 2-3 px): the case where the old
        // choked mask broke "NGUYÊN BẢN TRÀ VIỆT" into specks. 16 separate capital letters
        // (1 pt = 4 px apart, so they never touch whatever the anti-aliasing);
        // counters: B has 2, A, R, A one each = 5. The smallest counters may fall under the
        // 0.2 mm² dust floor (the letter is still cut around), but none may disappear silently.
        fx.pdf("t", textPdf("t", 60, 6, 7, 1f, "NGUYEN BAN TRA VIET"), 60, 6);
        Result cut = compose(300, 100, false, List.of(fx.place("t", 80, 40, false)), CutProperties.defaults());
        assertThat(cut.result.outerContours()).isEqualTo(16);
        assertThat(cut.result.holeContours() + cut.result.droppedSpecks()).isEqualTo(5);
    }

    @Test
    void dustBelowMinimumAreaIsDroppedButSmallShapesStay() throws IOException {
        // 0.3 x 0.3 mm = 0.09 mm² < 0.2 → dropped; 0.6 x 0.6 mm = 0.36 mm² → kept.
        fx.pdf("dust", squarePdf("dust", 0.3), 0.3, 0.3);
        fx.pdf("dot", squarePdf("dot", 0.6), 0.6, 0.6);
        Result cut = compose(300, 100, false,
                List.of(fx.place("dust", 100, 50, false), fx.place("dot", 150, 50, false)),
                CutProperties.defaults());
        assertThat(cut.result.outerContours()).isEqualTo(1);
        assertThat(cut.result.droppedSpecks()).isEqualTo(1);
        assertThat(cut.pdf.cutPaths().get(0).bounds()[0]).isCloseTo(150, within(PX_MM + 0.01));
    }

    @Test
    void offsetGrowsTheLineAndMergesNeighbours() throws IOException {
        fx.pdf("sq", squarePdf("sq", 10), 10, 10);
        List<Placement> twoSquares = List.of(fx.place("sq", 100, 50, false), fx.place("sq", 113, 50, false));

        Result tight = compose(300, 120, false, twoSquares, CutProperties.defaults());
        assertThat(tight.result.outerContours()).isEqualTo(2);

        CutProperties offset2 = new CutProperties(null, 2.0, null, null, null, null,
                null, null, null, null, null, null, null, null);
        Result grown = compose(300, 120, false, twoSquares, offset2);
        assertThat(grown.result.outerContours()).isEqualTo(1);
        double[] b = grown.pdf.cutPaths().get(0).bounds();
        // 2 mm = 23.6 → 24 px on each side.
        double grow = 24 * PX_MM;
        assertThat(b[0]).isCloseTo(100 - grow, within(PX_MM + 0.01));
        assertThat(b[2]).isCloseTo(123 + grow, within(PX_MM + 0.01));
    }

    @Test
    void opaqueImageBackgroundIsNotCut() throws IOException {
        // Image without alpha (PNG RGB, lossless so no JPEG ringing): white background around a
        // dark square. The flood of spec 10.3 drops the background: the cut runs around the square.
        BufferedImage image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 240, 240);
        g.setColor(new Color(20, 40, 160));
        g.fillRect(60, 60, 120, 120);
        g.dispose();
        fx.image("jpg", fx.imageFile("photo.png", image), 20, 20, false);
        Result cut = compose(300, 100, false, List.of(fx.place("jpg", 100, 40, false)), CutProperties.defaults());
        assertThat(cut.result.outerContours()).isEqualTo(1);
        double[] b = cut.pdf.cutPaths().get(0).bounds();
        assertThat(b[0]).isCloseTo(105, within(PX_MM + 0.01));
        assertThat(b[2] - b[0]).isCloseTo(10, within(2 * PX_MM + 0.01));
    }

    @Test
    void emptySheetStillHasMarksAndNoCutStroke() throws IOException {
        Result cut = compose(300, 100, false, List.of(), CutProperties.defaults());
        assertThat(cut.result.contours()).isZero();
        assertThat(cut.pdf.cutPaths()).isEmpty();
        assertThat(cut.pdf.markRects()).hasSize(8);
    }

    // ---------- corners and registration marks ----------

    @Test
    void cleanCornersKeepThePrintLength() throws IOException {
        fx.pdf("sq", squarePdf("sq", 10), 10, 10);
        // Touches the bottom edge but outside the 20 mm corner strips.
        Result cut = compose(300, 150, false, List.of(fx.place("sq", 100, 0, false)), CutProperties.defaults());
        assertThat(cut.result.paddingMm()).isZero();
        assertThat(cut.pdf.lengthMm).isCloseTo(150, within(0.001));
    }

    @Test
    void fiveMillimetreMarginGivesTheMeasuredSpecExample() throws IOException {
        // Spec 10.5, measured: print 570 x 819 mm → cut 570 x 851 mm, i.e. 16 mm per end.
        // Ink 4.5 mm from the bottom edge (± one 0.085 mm pixel) needs 15.5 → 16 mm.
        fx.pdf("sq", squarePdf("sq", 10), 10, 10);
        Result cut = compose(570, 819, false, List.of(fx.place("sq", 5, 4.5, false)), CutProperties.defaults());
        assertThat(cut.result.paddingMm()).isEqualTo(16);
        assertThat(cut.pdf.lengthMm).isCloseTo(851, within(0.001));
    }

    @Test
    void inkInACornerLengthensBothEndsByTheZone() throws IOException {
        fx.pdf("sq", squarePdf("sq", 10), 10, 10);
        Result cut = compose(570, 819, false, List.of(fx.place("sq", 0, 0, false)), CutProperties.defaults());
        // Ink on the corner itself: the whole zone, never more.
        assertThat(cut.result.paddingMm()).isEqualTo(20);
        assertThat(cut.pdf.widthMm).isCloseTo(570, within(0.001));
        assertThat(cut.pdf.lengthMm).isCloseTo(859, within(0.001));
        // The cut line moves up with the padding.
        double[] b = cut.pdf.cutPaths().get(0).bounds();
        assertThat(b[1]).isCloseTo(20, within(PX_MM + 0.01));
        assertThat(b[3]).isCloseTo(30, within(PX_MM + 0.01));
    }

    @Test
    void paddingIsTheMissingClearanceRoundedUpToWholeMm() throws IOException {
        fx.pdf("sq", squarePdf("sq", 5), 5, 5);
        // Top-right corner: square's top edge 12.5 mm below the top → needs 7.5 → 8 mm.
        Result cut = compose(300, 200, false, List.of(fx.place("sq", 290, 182.5, false)), CutProperties.defaults());
        assertThat(cut.result.paddingMm()).isEqualTo(8);
        assertThat(cut.pdf.lengthMm).isCloseTo(216, within(0.001));
        double[] b = cut.pdf.cutPaths().get(0).bounds();
        assertThat(cut.pdf.lengthMm - b[3]).isCloseTo(12.5 + 8, within(PX_MM + 0.01));
        assertThat(b[1]).isCloseTo(182.5 + 8, within(PX_MM + 0.01));
    }

    @Test
    void defaultMarksSitOnThePageCorners() throws IOException {
        fx.pdf("sq", squarePdf("sq", 10), 10, 10);
        Result cut = compose(570, 300, false, List.of(fx.place("sq", 0, 0, false)), CutProperties.defaults());
        double w = 570;
        double l = 300 + 40;
        List<CutPdf.Rect> rects = cut.pdf.markRects();
        assertThat(rects).hasSize(8);
        // SQUARE_WITH_EDGE, 15 mm, 1 mm: two bars on the inner sides of each corner square.
        assertRect(rects, 14, 0, 1, 15);
        assertRect(rects, 0, 14, 15, 1);
        assertRect(rects, w - 15, 0, 1, 15);
        assertRect(rects, w - 15, 14, 15, 1);
        assertRect(rects, 14, l - 15, 1, 15);
        assertRect(rects, 0, l - 15, 15, 1);
        assertRect(rects, w - 15, l - 15, 1, 15);
        assertRect(rects, w - 15, l - 15, 15, 1);
    }

    @Test
    void markSizeAndShapeComeFromConfigurationPerCorner() throws IOException {
        CutProperties props = new CutProperties(null, null, null, null, null, null,
                10.0, 2.0, 3.0, MarkShape.L, MarkShape.SQUARE_OUTLINE, MarkShape.SQUARE_FILLED,
                MarkShape.SQUARE_WITH_EDGE, null);
        fx.pdf("sq", squarePdf("sq", 5), 5, 5);
        // 13 mm zone: a square 14 mm up from the bottom-left is clean.
        Result cut = compose(200, 100, false, List.of(fx.place("sq", 0, 14, false)), props);
        assertThat(cut.result.paddingMm()).isZero();
        List<CutPdf.Rect> rects = cut.pdf.markRects();
        assertThat(rects).hasSize(2 + 4 + 1 + 2);
        assertRect(rects, 0, 0, 10, 10); // bottom-left filled
        assertRect(rects, 0, 98, 10, 2); // top-left L: bar along the top edge
        assertRect(rects, 0, 90, 2, 10); // and along the left edge
        assertRect(rects, 190, 90, 10, 2); // top-right outline
        assertRect(rects, 198, 90, 2, 10);
        assertRect(rects, 190, 0, 2, 10); // bottom-right: inner bars on the left and top sides
        assertRect(rects, 190, 8, 10, 2);
    }

    // ---------- optional artwork, memory, misuse ----------

    @Test
    void artworkLayerIsOptionalHiddenAndNonPrinting() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        CutProperties props = new CutProperties(null, null, null, null, null, null,
                null, null, null, null, null, null, null, true);
        Result cut = compose(300, 100, false, List.of(fx.place("a", 0, 0, false)), props);
        assertThat(cut.pdf.layers).containsExactly("CutContour", "RegMarks", "Artwork");
        assertThat(cut.pdf.layerVisible).containsEntry("Artwork", false).containsEntry("CutContour", true);
        assertThat(cut.pdf.xobjectsDrawn).containsExactly("Artwork");
        assertThat(cut.result.outerContours()).isEqualTo(1);
    }

    @Test
    void refusesASheetThatWouldNotFitInMemoryWithASuggestedDpi() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        Sheet sheet = fx.sheet(570, 1000, List.of(fx.place("a", 0, 0, false)));
        Path pdf = fx.compose("big", 570, 1000, false, sheet.placements());
        // 40 MB allowed: 570 x 1000 mm at 300 DPI needs ~80 MB mask + ~58 MB band.
        CutComposer composer = composer(CutProperties.defaults(), 80L << 20);
        assertThatThrownBy(() -> composer.compose(sheet, fx::get, pdf, new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
                    assertThat(ex.getMessage()).contains("file cắt").contains("giảm chiều dài tối đa mỗi file").contains("APP_CUT_DPI");
                    assertThat(ex.details()).containsKey("suggestedDpi");
                    assertThat((Integer) ex.details().get("suggestedDpi")).isLessThan(300).isGreaterThanOrEqualTo(50);
                });
    }

    @Test
    void refusesWithoutSuggestionWhenEvenTheLowestDpiIsTooBig() throws IOException {
        Sheet sheet = fx.sheet(570, 1000, List.of());
        Path pdf = fx.compose("empty", 570, 1000, false, List.of());
        CutComposer composer = composer(CutProperties.defaults(), 1L << 20);
        assertThatThrownBy(() -> composer.compose(sheet, fx::get, pdf, new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
                    assertThat(ex.details()).doesNotContainKey("suggestedDpi");
                });
    }

    @Test
    void estimateCountsTheMaskOnceUnlessDilating() {
        var size = new vn.minhtri.xuongin.raster.RasterSize(1000, 2000);
        long plain = CutComposer.estimateBytes(size, 1024, false);
        long dilating = CutComposer.estimateBytes(size, 1024, true);
        assertThat(plain).isEqualTo(2_000_000L + 1088L * 1000 * 8);
        assertThat(dilating - plain).isEqualTo(4_000_000L);
    }

    @Test
    void rejectsAPdfOfAnotherSize() throws IOException {
        Path pdf = fx.compose("small", 100, 100, false, List.of());
        Sheet sheet = fx.sheet(100, 120, List.of());
        CutComposer composer = composer(CutProperties.defaults(), TWO_GB);
        assertThatThrownBy(() -> composer.compose(sheet, fx::get, pdf, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outOfMemoryDuringTheBuildIsTiffTooLargeNotInternalError() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        Sheet sheet = fx.sheet(100, 50, List.of(fx.place("a", 0, 0, false)));
        Path pdf = fx.compose("oom", 100, 50, false, sheet.placements());
        RasterProperties raster = RasterProperties.defaults();
        SheetRasterizer rasterizer = org.mockito.Mockito.mock(SheetRasterizer.class);
        org.mockito.Mockito.when(rasterizer.bandRows()).thenReturn(1024);
        org.mockito.Mockito.when(rasterizer.render(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new OutOfMemoryError("Java heap space"));
        CutComposer composer = new CutComposer(rasterizer, raster, new RasterMemoryGuard(raster, () -> TWO_GB),
                CutProperties.defaults());
        assertThatThrownBy(() -> composer.compose(sheet, fx::get, pdf, new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
                    assertThat(ex.getMessage()).contains("file cắt tấm 1", "giảm chiều dài tối đa mỗi file");
                });
    }

    @Test
    void estimateOfASheetMatchesTheRasterEstimateAndRefusesOversizedSheets() {
        Sheet sheet = fx.sheet(570, 1000, List.of());
        // 6732 x 11811 px mask + one band of 1088 rows x 8 bytes.
        assertThat(composer(CutProperties.defaults(), TWO_GB).estimateBytes(sheet))
                .isEqualTo(6732L * 11811 + 1088L * 6732 * 8);
        assertThatThrownBy(() -> composer(CutProperties.defaults(), 80L << 20).estimateBytes(sheet))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE));
    }

    // ---------- helpers ----------

    private record Result(CutResult result, CutPdf pdf) {}

    private Result compose(double w, double l, boolean cutLines, List<Placement> placements, CutProperties props)
            throws IOException {
        Path sheetPdf = fx.compose("sheet", w, l, cutLines, placements);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CutResult result = composer(props, TWO_GB).compose(fx.sheet(w, l, placements), fx::get, sheetPdf, out);
        return new Result(result, CutPdf.read(out.toByteArray()));
    }

    private static CutComposer composer(CutProperties props, long heap) {
        RasterProperties raster = RasterProperties.defaults();
        return new CutComposer(new SheetRasterizer(raster), raster, new RasterMemoryGuard(raster, () -> heap), props);
    }

    private static void assertRect(List<CutPdf.Rect> rects, double x, double y, double w, double h) {
        assertThat(rects).as("rect %s,%s %sx%s", x, y, w, h).anySatisfy(r -> {
            assertThat(r.x()).isCloseTo(x, within(0.001));
            assertThat(r.y()).isCloseTo(y, within(0.001));
            assertThat(r.w()).isCloseTo(w, within(0.001));
            assertThat(r.h()).isCloseTo(h, within(0.001));
        });
    }

    private Path squarePdf(String name, double sizeMm) throws IOException {
        return fx.solidPdf(name, sizeMm, sizeMm, 0, new float[] {0f, 0f, 0f}, 0);
    }

    /** Square ring: outer {@code outerMm}, centred square hole {@code holeMm}, even-odd fill. */
    private Path ringPdf(String name, double outerMm, double holeMm) throws IOException {
        Path out = tmp.resolve(name + ".pdf");
        float o = (float) Units.mmToPt(outerMm);
        float h = (float) Units.mmToPt(holeMm);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(o, o));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(0.8f, 0.1f, 0.1f);
                cs.addRect(0, 0, o, o);
                cs.addRect((o - h) / 2, (o - h) / 2, h, h);
                cs.fillEvenOdd();
            }
            doc.save(out.toFile());
        }
        return out;
    }

    /**
     * Black text on a transparent page of {@code w × h} mm, in the Liberation Sans that ships inside
     * PDFBox, EMBEDDED: a non-embedded standard font is drawn with whatever substitute the OS has
     * (Windows: two 7 pt letters touched and merged into one contour), so the glyphs must not
     * depend on the machine. {@code spacingPt} is extra space between letters.
     */
    private Path textPdf(String name, double w, double h, float sizePt, String text) throws IOException {
        return textPdf(name, w, h, sizePt, 0f, text);
    }

    private Path textPdf(String name, double w, double h, float sizePt, float spacingPt, String text)
            throws IOException {
        Path out = tmp.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle((float) Units.mmToPt(w), (float) Units.mmToPt(h)));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(0f, 0f, 0f);
                cs.beginText();
                try (InputStream font = PDDocument.class.getResourceAsStream(EMBEDDED_FONT)) {
                    cs.setFont(PDType0Font.load(doc, font), sizePt);
                }
                cs.setCharacterSpacing(spacingPt);
                cs.newLineAtOffset(2, (float) Units.mmToPt(h) / 2 - sizePt / 3);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out.toFile());
        }
        return out;
    }
}
