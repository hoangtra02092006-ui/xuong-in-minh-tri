package vn.minhtri.xuongin.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Image header reading (spec 7.2) and previews (spec 6.2). */
class ImageAndPreviewTest {

    @TempDir
    Path dir;

    private final FileProperties props = new FileProperties();
    private final ImageMetadataReader imageReader = new ImageMetadataReader(props);
    private final PdfMetadataReader pdfReader = new PdfMetadataReader(props);
    private final PreviewRenderer previews = new PreviewRenderer(props);

    @Test
    void pngWithAlphaChannelHasAlpha() throws IOException {
        Path png = Files.write(dir.resolve("a.png"), TestFiles.png(100, 50, BufferedImage.TYPE_INT_ARGB));

        SourceMetadata m = imageReader.read(png);

        assertThat(m.hasAlpha()).isTrue();
        // No pHYs chunk -> 72 DPI.
        assertThat(m.widthMm()).isCloseTo(100 * 25.4 / 72, within(0.01));
        assertThat(m.contentBox().widthPt()).isCloseTo(100, within(0.01));
    }

    @Test
    void opaquePngHasNoAlpha() throws IOException {
        Path png = Files.write(dir.resolve("o.png"), TestFiles.png(10, 10, BufferedImage.TYPE_INT_RGB));

        assertThat(imageReader.read(png).hasAlpha()).isFalse();
    }

    @Test
    void defaultDpiIsConfigurable() throws IOException {
        props.setDefaultImageDpi(254);
        Path png = Files.write(dir.resolve("d.png"), TestFiles.png(100, 100, BufferedImage.TYPE_INT_RGB));

        assertThat(new ImageMetadataReader(props).read(png).widthMm()).isCloseTo(10, within(0.001));
    }

    @Test
    void notAnImageIsRejected() throws IOException {
        Path bad = Files.writeString(dir.resolve("bad.png"), "hello");

        assertThatThrownBy(() -> imageReader.read(bad)).isInstanceOf(IOException.class);
    }

    @Test
    void dpiIgnoresMissingOrInvalidValues() {
        assertThat(ImageMetadataReader.dpi(null, "HorizontalPixelSize")).isNaN();
    }

    @Test
    void pdfPreviewIsCroppedToTheContentBox() throws IOException {
        Path pdf = TestFiles.sample("thu-hinh-nho-giua-A4.pdf");
        StoredFile file = stored(pdfReader.read(pdf));

        BufferedImage img = decode(previews.render(file, pdf));

        // 30 x 30 mm content box, not the A4 page: square, long side at most 360 px.
        assertThat(img.getWidth()).isLessThanOrEqualTo(360);
        assertThat(img.getWidth()).isEqualTo(img.getHeight());
        assertThat(img.getWidth()).isGreaterThan(200);
    }

    @Test
    void pdfPreviewKeepsTheContentAspectRatio() throws IOException {
        Path pdf = TestFiles.sample("nhan-13x4.pdf");
        StoredFile file = stored(pdfReader.read(pdf));

        BufferedImage img = decode(previews.render(file, pdf));

        assertThat(Math.max(img.getWidth(), img.getHeight())).isBetween(355, 360);
        assertThat((double) img.getWidth() / img.getHeight()).isCloseTo(130.0 / 40.0, within(0.05));
    }

    @Test
    void hugePdfPreviewIsStillAtMost360px() throws IOException {
        // 600 mm at the 24 DPI floor is 567 px: scaled down afterwards.
        Path pdf = TestFiles.sample("loi-qua-kho-60x59.pdf");
        StoredFile file = stored(pdfReader.read(pdf));

        BufferedImage img = decode(previews.render(file, pdf));

        assertThat(img.getWidth()).isEqualTo(360);
    }

    @Test
    void rotatedPdfPreviewIsTurned() throws IOException {
        Path pdf = TestFiles.pdf(dir.resolve("rot.pdf"), 100, 50, 90, 1, (cs, doc) -> TestFiles.rect(cs, 0, 0, 100, 50));
        StoredFile file = stored(pdfReader.read(pdf));

        BufferedImage img = decode(previews.render(file, pdf));

        assertThat(img.getHeight()).isGreaterThan(img.getWidth());
    }

    @Test
    void largeJpegPreviewIsSubsampledToAtMost360px() throws IOException {
        Path jpg = TestFiles.sample("thu-anh-lon-3mb.jpg");
        StoredFile file = stored(imageReader.read(jpg));

        byte[] bytes = previews.render(file, jpg);
        BufferedImage img = decode(bytes);

        assertThat(bytes[0] & 0xff).isEqualTo(0xff);
        assertThat(bytes[1] & 0xff).isEqualTo(0xd8);
        assertThat(img.getWidth()).isEqualTo(360);
        assertThat((double) img.getWidth() / img.getHeight()).isCloseTo(4200.0 / 3000.0, within(0.02));
    }

    @Test
    void smallImagePreviewIsNotEnlarged() throws IOException {
        Path png = Files.write(dir.resolve("s.png"), TestFiles.png(40, 20, BufferedImage.TYPE_INT_ARGB));
        StoredFile file = stored(imageReader.read(png));

        BufferedImage img = decode(previews.render(file, png));

        assertThat(img.getWidth()).isEqualTo(40);
        assertThat(img.getHeight()).isEqualTo(20);
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static StoredFile stored(SourceMetadata m) {
        return new StoredFile("id", "n", "p", m.type(), m.widthMm(), m.heightMm(), m.sourceWidthMm(), m.sourceHeightMm(),
                m.contentBox(), m.occupancy(), m.pageRotation(), m.pageCount(), m.hasAlpha(), 0, Instant.EPOCH);
    }
}
