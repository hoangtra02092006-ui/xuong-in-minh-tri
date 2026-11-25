package vn.minhtri.xuongin.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static vn.minhtri.xuongin.file.TestFiles.PT;
import static vn.minhtri.xuongin.file.TestFiles.rect;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Content box rules of spec 7.1 on PDFs built for each case. */
class PdfMetadataReaderTest {

    @TempDir
    Path dir;

    private final FileProperties props = new FileProperties();
    private final PdfMetadataReader reader = new PdfMetadataReader(props);

    @Test
    void whiteFilledShapeIsArtwork() throws IOException {
        // A pixel scan would see white on white and cut it away (spec 15: "Hình tô màu trắng vẫn được tính").
        Path pdf = TestFiles.pdf(dir.resolve("white.pdf"), 100, 100, (cs, doc) -> {
            cs.setNonStrokingColor(Color.WHITE);
            rect(cs, 10, 20, 40, 30);
        });

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(40, within(0.01));
        assertThat(m.heightMm()).isCloseTo(30, within(0.01));
        assertThat(m.contentBox().xPt()).isCloseTo(10 * PT, within(0.01));
        assertThat(m.occupancy().isOccupied(10, 10)).isTrue();
    }

    @Test
    void blankPageFallsBackToThePageBox() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("blank.pdf"), 80, 60, null);

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(80, within(0.01));
        assertThat(m.heightMm()).isCloseTo(60, within(0.01));
        assertThat(m.contentBox().xPt()).isZero();
    }

    @Test
    void contentUnderOneMillimetreFallsBackToThePageBox() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("dot.pdf"), 80, 60, (cs, doc) -> rect(cs, 10, 10, 0.5, 20));

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(80, within(0.01));
    }

    @Test
    void artworkOutsideThePageIsClippedToThePage() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("bleed.pdf"), 50, 50, (cs, doc) -> rect(cs, -10, 25, 100, 10));

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(50, within(0.01));
        assertThat(m.heightMm()).isCloseTo(10, within(0.01));
        assertThat(m.contentBox().xPt()).isZero();
    }

    @Test
    void strokeAddsHalfItsWidth() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("stroke.pdf"), 100, 100, (cs, doc) -> {
            cs.setLineWidth((float) (2 * PT));
            cs.moveTo((float) (20 * PT), (float) (50 * PT));
            cs.lineTo((float) (60 * PT), (float) (50 * PT));
            cs.stroke();
        });

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(42, within(0.01));
        assertThat(m.heightMm()).isCloseTo(2, within(0.01));
    }

    @Test
    void clipLimitsTheBox() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("clip.pdf"), 100, 100, (cs, doc) -> {
            cs.addRect((float) (10 * PT), (float) (10 * PT), (float) (20 * PT), (float) (20 * PT));
            cs.clip();
            rect(cs, 0, 0, 100, 100);
        });

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(20, within(0.01));
        assertThat(m.heightMm()).isCloseTo(20, within(0.01));
    }

    @Test
    void textAndImagesCount() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("mixed.pdf"), 200, 200, (cs, doc) -> {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 20);
            cs.newLineAtOffset((float) (20 * PT), (float) (20 * PT));
            cs.showText("Minh Tri");
            cs.endText();
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            cs.drawImage(LosslessFactory.createFromImage(doc, img),
                    (float) (100 * PT), (float) (150 * PT), (float) (30 * PT), (float) (20 * PT));
        });

        SourceMetadata m = reader.read(pdf);

        // Text starts at x = 20 mm plus the side bearing of "M"; the box must match what the renderer paints.
        assertThat(m.contentBox().xPt() / PT).isCloseTo(leftmostInkMm(pdf), within(0.05));
        assertThat((m.contentBox().xPt() + m.contentBox().widthPt()) / PT).isCloseTo(130, within(0.01));
        assertThat((m.contentBox().yPt() + m.contentBox().heightPt()) / PT).isCloseTo(170, within(0.01));
        assertThat(m.contentBox().yPt() / PT).isCloseTo(20, within(1.0));
    }

    /** Leftmost painted pixel when rendering the page at 600 DPI, in mm. */
    private static double leftmostInkMm(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 600);
            int minX = img.getWidth();
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < minX; x++) {
                    if ((img.getRGB(x, y) & 0xff) < 250) {
                        minX = x;
                    }
                }
            }
            return minX * 25.4 / 600;
        }
    }

    @Test
    void invisibleTextIsIgnored() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("ocr.pdf"), 200, 200, (cs, doc) -> {
            rect(cs, 100, 100, 50, 50);
            cs.beginText();
            cs.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER);
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 20);
            cs.newLineAtOffset((float) (10 * PT), (float) (10 * PT));
            cs.showText("hidden");
            cs.endText();
        });

        SourceMetadata m = reader.read(pdf);

        assertThat(m.widthMm()).isCloseTo(50, within(0.01));
    }

    @Test
    void quarterTurnSwapsReportedSizesButKeepsTheBoxInPageSpace() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("rot.pdf"), 100, 50, 90, 3, (cs, doc) -> rect(cs, 0, 0, 40, 20));

        SourceMetadata m = reader.read(pdf);

        assertThat(m.pageRotation()).isEqualTo(90);
        assertThat(m.pageCount()).isEqualTo(3);
        assertThat(m.widthMm()).isCloseTo(20, within(0.01));
        assertThat(m.heightMm()).isCloseTo(40, within(0.01));
        assertThat(m.sourceWidthMm()).isCloseTo(50, within(0.01));
        assertThat(m.sourceHeightMm()).isCloseTo(100, within(0.01));
        assertThat(m.contentBox().widthPt()).isCloseTo(40 * PT, within(0.01));
        // Mask stays in unrotated page space.
        assertThat(m.occupancy().cols()).isEqualTo(80);
    }

    @Test
    void trimDisabledUsesTheWholePage() throws IOException {
        props.setTrimEnabled(false);
        Path pdf = TestFiles.sample("thu-hinh-nho-giua-A4.pdf");

        SourceMetadata m = new PdfMetadataReader(props).read(pdf);

        assertThat(m.widthMm()).isCloseTo(210, within(0.1));
        assertThat(m.heightMm()).isCloseTo(297, within(0.1));
    }

    @Test
    void tooManyShapesFallsBackToThePageBox() throws IOException {
        props.setMaxShapes(2);
        Path pdf = TestFiles.pdf(dir.resolve("busy.pdf"), 100, 100, (cs, doc) -> {
            rect(cs, 10, 10, 5, 5);
            rect(cs, 20, 20, 5, 5);
            rect(cs, 30, 30, 5, 5);
        });

        SourceMetadata m = new PdfMetadataReader(props).read(pdf);

        assertThat(m.widthMm()).isCloseTo(100, within(0.01));
    }

    @Test
    void maskIsSkippedWhenItWouldBeTooLarge() throws IOException {
        props.setMaskMaxCells(10);

        SourceMetadata m = new PdfMetadataReader(props).read(TestFiles.sample("nhan-7x3.pdf"));

        assertThat(m.occupancy()).isNull();
        assertThat(m.widthMm()).isCloseTo(70, within(0.1));
    }

    @Test
    void bandedMaskEqualsSingleBandMask() throws IOException {
        SourceMetadata whole = reader.read(TestFiles.sample("thu-chu-L-108.pdf"));
        props.setMaskBandPixels(1000);

        SourceMetadata banded = new PdfMetadataReader(props).read(TestFiles.sample("thu-chu-L-108.pdf"));

        assertThat(banded.occupancy().bits()).isEqualTo(whole.occupancy().bits());
    }

    @Test
    void garbageIsRejected() throws IOException {
        Path bad = Files.writeString(dir.resolve("bad.pdf"), "not a pdf at all");

        assertThatThrownBy(() -> reader.read(bad)).isInstanceOf(IOException.class);
    }

    @Test
    void rotationIsNormalised() {
        assertThat(PdfMetadataReader.normalizeRotation(-90)).isEqualTo(270);
        assertThat(PdfMetadataReader.normalizeRotation(450)).isEqualTo(90);
        assertThat(PdfMetadataReader.normalizeRotation(0)).isZero();
    }
}
