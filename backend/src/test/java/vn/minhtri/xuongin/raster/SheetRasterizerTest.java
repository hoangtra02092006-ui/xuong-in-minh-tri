package vn.minhtri.xuongin.raster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.minhtri.xuongin.export.pdf.SheetFixtures;
import vn.minhtri.xuongin.file.ContentBox;

/** Spec 10.6: banded render equals a whole-sheet render byte for byte. */
class SheetRasterizerTest {

    private static final int DPI = 300;

    @TempDir
    Path tmp;

    private Path sheetPdf;

    @BeforeEach
    void setUp() throws IOException {
        SheetFixtures fx = new SheetFixtures(tmp);
        fx.pdf("a", SheetFixtures.SAMPLES.resolve("nhan-7x3.pdf"), 70, 30);
        fx.pdf("b", SheetFixtures.SAMPLES.resolve("nhan-5x7.pdf"), 50, 70);
        fx.pdf("small", SheetFixtures.SAMPLES.resolve("thu-hinh-nho-giua-A4.pdf"),
                new ContentBox(255.12, 396.85, 85.04, 85.04), 0, 210, 297);
        fx.image("img", SheetFixtures.SAMPLES.resolve("anh-600x400-300dpi.png"), 50.8, 400 * 25.4 / 300, false);
        // Pieces straddle band boundaries (64 rows at 300 DPI = 5.4 mm).
        sheetPdf = fx.compose("sheet", 130.3, 101.7, true, List.of(
                fx.place("a", 3.3, 2.1, false),
                fx.place("b", 77.7, 3, false),
                fx.place("a", 3, 36.6, true),
                fx.place("small", 40, 40.5, false),
                fx.place("img", 40, 75.2, false)));
    }

    @Test
    void bandRowsRoundUpToMultiplesOf64() {
        assertThat(SheetRasterizer.roundBandRows(1024)).isEqualTo(1024);
        assertThat(SheetRasterizer.roundBandRows(1000)).isEqualTo(1024);
        assertThat(SheetRasterizer.roundBandRows(65)).isEqualTo(128);
        assertThat(SheetRasterizer.roundBandRows(0)).isEqualTo(64);
        assertThat(new SheetRasterizer(RasterProperties.defaults()).bandRows()).isEqualTo(1024);
    }

    @Test
    void sizeMatchesPdfBoxToThePixel() throws IOException {
        BufferedImage reference = renderWhole(sheetPdf);
        RasterSize size = SheetRasterizer.sizeOf(130.3, 101.7, DPI);
        assertThat(size.width()).isEqualTo(reference.getWidth());
        assertThat(size.height()).isEqualTo(reference.getHeight());
        // 570 x 1000 mm at 300 DPI (spec 10.6).
        assertThat(SheetRasterizer.sizeOf(570, 1000, 300)).isEqualTo(new RasterSize(6732, 11811));
    }

    @Test
    void bandedRenderEqualsSingleBandAndPdfBoxWholePage() throws IOException {
        Collected banded = collect(64);
        Collected single = collect(1 << 20);

        assertThat(single.calls).hasSize(1);
        assertThat(banded.calls).hasSizeGreaterThan(10);
        // Bands arrive top to bottom, contiguous, each array exactly rows x width from index 0.
        int expectedY = 0;
        for (int[] call : banded.calls) {
            assertThat(call[0]).isEqualTo(expectedY);
            assertThat(call[2]).isEqualTo(call[1] * banded.width);
            expectedY += call[1];
        }
        assertThat(expectedY).isEqualTo(banded.height);

        assertThat(banded.pixels).isEqualTo(single.pixels);
        int[] reference = ((DataBufferInt) renderWhole(sheetPdf).getRaster().getDataBuffer()).getData();
        assertThat(banded.pixels).isEqualTo(reference);
    }

    @Test
    void emptyAreaIsTransparentNotBlack() throws IOException {
        Collected single = collect(1 << 20);
        // Top-right corner of the sheet has no piece.
        assertThat(single.pixels[single.width - 1]).isZero();
    }

    @Test
    void rejectsNonPositiveDpi() {
        SheetRasterizer rasterizer = new SheetRasterizer(RasterProperties.defaults());
        assertThatThrownBy(() -> rasterizer.render(sheetPdf, 0, (y, r, w, p) -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Collected collect(int bandRows) throws IOException {
        SheetRasterizer rasterizer = new SheetRasterizer(new RasterProperties(null, null, bandRows, null, null));
        RasterSize size = SheetRasterizer.sizeOf(130.3, 101.7, DPI);
        Collected c = new Collected(size.width(), size.height());
        RasterSize rendered = rasterizer.render(sheetPdf, DPI, (y, rows, width, argb) -> {
            c.calls.add(new int[] {y, rows, argb.length});
            System.arraycopy(argb, 0, c.pixels, y * width, rows * width);
        });
        assertThat(rendered).isEqualTo(size);
        return c;
    }

    private static BufferedImage renderWhole(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFRenderer(doc).renderImage(0, DPI / 72f, ImageType.ARGB, RenderDestination.PRINT);
        }
    }

    private static final class Collected {
        final int width;
        final int height;
        final int[] pixels;
        final List<int[]> calls = new ArrayList<>();

        Collected(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
        }
    }
}
