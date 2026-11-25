package vn.minhtri.xuongin.export.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.raster.PixelRect;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.shared.Units;

/** Invariants 6, 9, 10: the print PDF stays vector and every piece lands in its cell ±0.1 mm. */
class PdfComposerTest {

    /** 0.05 mm per pixel: enough to check ±0.1 mm. */
    private static final int DPI = 508;

    private static final double TOL_MM = 0.1;
    private static final float[] BLUE = {0.1f, 0.3f, 0.8f};

    @TempDir
    Path tmp;

    private SheetFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new SheetFixtures(tmp);
    }

    @Test
    void pageSizeIsSheetWidthByLengthInPoints() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        Path pdf = fx.compose("size", 570, 4782, false, List.of(fx.place("a", 5, 5, false)));
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            PDPage page = doc.getPage(0);
            assertThat((double) page.getMediaBox().getWidth()).isCloseTo(Units.mmToPt(570), within(0.01));
            assertThat((double) page.getMediaBox().getHeight()).isCloseTo(Units.mmToPt(4782), within(0.01));
        }
    }

    @Test
    void samplePiecesLandInTheirCellsAndStayVector() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        // Trimmed sample: a 30 x 30 mm square in the middle of an A4 page; box stored at upload.
        fx.pdf("small", SheetFixtures.SAMPLES.resolve("thu-hinh-nho-giua-A4.pdf"),
                new ContentBox(255.12, 396.85, 85.04, 85.04), 0, 210, 297.0);
        // Solid 130 x 40 mm page, but the stored box is only a 40 x 20 mm part: draw THAT box.
        fx.pdf("part", SheetFixtures.SAMPLES.resolve("nhan-13x4.pdf"),
                new ContentBox(Units.mmToPt(20), Units.mmToPt(10), Units.mmToPt(40), Units.mmToPt(20)), 0, 130, 40);
        List<Placement> placements = List.of(
                fx.place("a", 5, 5, false),
                fx.place("a", 80, 5, true),
                fx.place("small", 120, 5, false),
                fx.place("part", 120, 50, false));
        Path pdf = fx.compose("cells", 200, 120, false, placements);

        RasterProbe probe = RasterProbe.render(pdf, DPI);
        for (Placement p : placements) {
            assertInkMatchesCell(probe, p);
        }

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            List<PDXObject> xobjects = xobjects(doc.getPage(0).getResources());
            // One form per distinct source (nhan-7x3 used twice is embedded once), no image at all.
            assertThat(xobjects).hasSize(3).allMatch(x -> x instanceof PDFormXObject);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void pageRotationIsHonouredLikeAViewer(int rotation) throws IOException {
        Path src = fx.solidPdf("rot" + rotation, 40, 20, rotation, BLUE, 5);
        fx.pdf("r", src, new ContentBox(0, 0, Units.mmToPt(40), Units.mmToPt(20)), rotation, 40, 20);
        Placement p = fx.place("r", 10, 10, false);
        Path pdf = fx.compose("rotsheet" + rotation, 80, 60, false, List.of(p));

        RasterProbe probe = RasterProbe.render(pdf, DPI);
        assertInkMatchesCell(probe, p);
        double[] expected = redCentroidOfSource(src);
        double[] actual = probe.redCentroid(p.xMm(), p.yMm(), p.xMm() + p.wMm(), p.yMm() + p.hMm());
        assertThat(actual).isNotNull();
        assertThat(actual[0] - p.xMm()).isCloseTo(expected[0], within(TOL_MM));
        assertThat(actual[1] - p.yMm()).isCloseTo(expected[1], within(TOL_MM));
    }

    @Test
    void rotatedPlacementOfARotatedPageTurnsTheDisplayedPage90DegreesCounterClockwise() throws IOException {
        Path src = fx.solidPdf("rot90b", 40, 20, 90, BLUE, 5);
        fx.pdf("r", src, new ContentBox(0, 0, Units.mmToPt(40), Units.mmToPt(20)), 90, 40, 20);
        Placement p = fx.place("r", 10, 10, true);
        assertThat(p.wMm()).isCloseTo(40, within(1e-9));
        Path pdf = fx.compose("rot90rotated", 80, 60, false, List.of(p));

        RasterProbe probe = RasterProbe.render(pdf, DPI);
        assertInkMatchesCell(probe, p);
        double[] s = redCentroidOfSource(src);
        double displayedH = 40;
        double[] actual = probe.redCentroid(p.xMm(), p.yMm(), p.xMm() + p.wMm(), p.yMm() + p.hMm());
        assertThat(actual[0] - p.xMm()).isCloseTo(displayedH - s[1], within(TOL_MM));
        assertThat(actual[1] - p.yMm()).isCloseTo(s[0], within(TOL_MM));
    }

    @Test
    void freeAngleFitsTheRotatedBoundingBox() throws IOException {
        Path src = fx.solidPdf("angle", 40, 20, 0, BLUE, 5);
        fx.pdf("t", src, 40, 20);
        Placement p = fx.placeAngled("t", 10, 10, 30);
        assertThat(p.wMm()).isCloseTo(40 * Math.cos(Math.PI / 6) + 10, within(1e-9));
        Path pdf = fx.compose("angled", 80, 70, false, List.of(p));

        RasterProbe probe = RasterProbe.render(pdf, DPI);
        assertInkMatchesCell(probe, p);
        // Red 5 mm mark centre (2.5, 2.5) rotated by 30 degrees, then shifted by -minX = 20 sin 30.
        double c = Math.cos(Math.PI / 6);
        double s = Math.sin(Math.PI / 6);
        double[] actual = probe.redCentroid(p.xMm(), p.yMm(), p.xMm() + p.wMm(), p.yMm() + p.hMm());
        assertThat(actual[0] - p.xMm()).isCloseTo(2.5 * c - 2.5 * s + 10, within(0.15));
        assertThat(actual[1] - p.yMm()).isCloseTo(2.5 * s + 2.5 * c, within(0.15));
    }

    @Test
    void imagesAreImageXObjectsAtTheirPhysicalSize() throws IOException {
        Path png = SheetFixtures.SAMPLES.resolve("anh-600x400-300dpi.png");
        fx.image("img", png, 600 * 25.4 / 300, 400 * 25.4 / 300, false);
        List<Placement> placements = List.of(fx.place("img", 5, 5, false), fx.place("img", 70, 5, true));
        Path pdf = fx.compose("images", 120, 70, false, placements);

        RasterProbe probe = RasterProbe.render(pdf, DPI);
        for (Placement p : placements) {
            assertInkMatchesCell(probe, p);
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertThat(xobjects(doc.getPage(0).getResources())).hasSize(1).allMatch(x -> x instanceof PDImageXObject);
        }
    }

    @Test
    void cutLinesAreThinGreyFramesOnePerPiece() throws IOException {
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        List<Placement> placements = List.of(fx.place("a", 5, 5, false), fx.place("a", 80, 5, true));
        assertThat(operators(fx.compose("nocut", 120, 80, false, placements), "S")).isEmpty();

        List<Object[]> strokes = operators(fx.compose("cut", 120, 80, true, placements), "S");
        assertThat(strokes).hasSize(2);
        List<Object[]> widths = operators(fx.compose("cut2", 120, 80, true, placements), "w");
        assertThat(widths).allSatisfy(args ->
                assertThat(((COSNumber) args[0]).floatValue()).isEqualTo(PdfComposer.CUT_LINE_WIDTH_PT));
    }

    @Test
    void unknownFileIsFileNotFound() {
        Placement p = new Placement("gone", "gone", 0, 0, 0, 10, 10, false, 0, 10, 10);
        var sheet = fx.sheet(100, 100, List.of(p));
        assertThatThrownBy(() -> new PdfComposer().compose(sheet, id -> null, SheetFixtures::pathOf, false,
                        new ByteArrayOutputStream()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void opaqueZonesCoverOnlyImagesWithoutAlpha() throws IOException {
        fx.pdf("pdf", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        fx.image("jpg", fx.imageFile("x.jpg", new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)), 25.4, 12.7, false);
        fx.image("png", fx.imageFile("x.png", new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)), 10, 10, true);
        var sheet = fx.sheet(200, 100, List.of(
                fx.place("pdf", 0, 0, false), fx.place("jpg", 25.4, 50.8, false), fx.place("png", 100, 0, false)));

        // 100 DPI, 100 mm sheet = 393 rows. jpg: x 100..200 px, y from 50.8 to 63.5 mm = 200..250 px up.
        List<PixelRect> zones = PdfComposer.opaqueImageZones(sheet, fx::get, 100, 393);
        assertThat(zones).containsExactly(new PixelRect(100, 393 - 250, 200, 393 - 200));
    }

    private static void assertInkMatchesCell(RasterProbe probe, Placement p) {
        double margin = 2;
        double[] ink = probe.inkBox(p.xMm() - margin, p.yMm() - margin,
                p.xMm() + p.wMm() + margin, p.yMm() + p.hMm() + margin);
        assertThat(ink).as("ink of %s", p).isNotNull();
        assertThat(ink[0]).as("x of %s", p).isCloseTo(p.xMm(), within(TOL_MM));
        assertThat(ink[1]).as("y of %s", p).isCloseTo(p.yMm(), within(TOL_MM));
        assertThat(ink[2]).as("w of %s", p).isCloseTo(p.wMm(), within(TOL_MM));
        assertThat(ink[3]).as("h of %s", p).isCloseTo(p.hMm(), within(TOL_MM));
    }

    /** Red mark centre of a source page as PDFBox displays it (honours /Rotate), mm from bottom-left. */
    private static double[] redCentroidOfSource(Path src) throws IOException {
        try (PDDocument doc = Loader.loadPDF(src.toFile())) {
            BufferedImage img = new PDFRenderer(doc).renderImage(0, DPI / 72f, ImageType.ARGB);
            double sx = 0;
            double sy = 0;
            long n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int p = img.getRGB(x, y);
                    if (((p >> 16) & 0xFF) > 200 && ((p >> 8) & 0xFF) < 60 && (p & 0xFF) < 60) {
                        sx += x + 0.5;
                        sy += y + 0.5;
                        n++;
                    }
                }
            }
            double mmPerPx = 25.4 / DPI;
            return new double[] {sx / n * mmPerPx, (img.getHeight() - sy / n) * mmPerPx};
        }
    }

    private static List<PDXObject> xobjects(PDResources resources) throws IOException {
        List<PDXObject> list = new ArrayList<>();
        for (COSName name : resources.getXObjectNames()) {
            list.add(resources.getXObject(name));
        }
        return list;
    }

    /** Operand lists of every occurrence of an operator in page 1's content stream. */
    private static List<Object[]> operators(Path pdf, String name) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            List<Object> tokens = new PDFStreamParser(doc.getPage(0)).parse();
            List<Object[]> found = new ArrayList<>();
            List<Object> operands = new ArrayList<>();
            for (Object token : tokens) {
                if (token instanceof Operator op) {
                    if (op.getName().equals(name)) {
                        found.add(operands.toArray());
                    }
                    operands.clear();
                } else {
                    operands.add(token);
                }
            }
            return found;
        }
    }
}
