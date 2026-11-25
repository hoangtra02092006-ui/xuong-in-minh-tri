package vn.minhtri.xuongin.raster;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Decode cost of source images, from headers only. */
class SourceImagesTest {

    @TempDir
    Path dir;

    @Test
    void imageFileCostsElevenBytesPerPixelPlusSixForAlpha() throws IOException {
        Path png = dir.resolve("a.png");
        ImageIO.write(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        assertThat(SourceImages.imageFileBytes(png, false)).isEqualTo(300L * 200 * 11);
        assertThat(SourceImages.imageFileBytes(png, true)).isEqualTo(300L * 200 * (11 + 6));
    }

    @Test
    void unreadableImageCostsNothing() throws IOException {
        Path junk = Files.writeString(dir.resolve("junk.png"), "not an image");
        assertThat(SourceImages.imageFileBytes(junk, false)).isZero();
    }

    @Test
    void largestImageOfThePageCountsIncludingInsideFormsAndSoftMasks() throws IOException {
        Path pdf = dir.resolve("sheet.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject small = LosslessFactory.createFromImage(doc,
                    new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
            // ARGB → image plus a 1-channel /SMask of the same size.
            PDImageXObject withAlpha = LosslessFactory.createFromImage(doc,
                    new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB));
            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(100, 100));
            form.setResources(new PDResources());
            try (PDFormContentStream cs = new PDFormContentStream(form)) {
                cs.drawImage(withAlpha, 0, 0, 40, 30);
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(small, 0, 0, 10, 10);
                cs.drawForm(form);
                // The same form twice is scanned once.
                cs.drawForm(form);
            }
            doc.save(pdf.toFile());
        }
        assertThat(SourceImages.largestDecodeBytes(pdf)).isEqualTo(40L * 30 * 11 + 40L * 30 * 6);
    }

    @Test
    void vectorOnlyPdfCostsNothing() throws IOException {
        Path pdf = dir.resolve("vector.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A6);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.addRect(10, 10, 50, 50);
                cs.fill();
            }
            doc.save(pdf.toFile());
        }
        assertThat(SourceImages.largestDecodeBytes(pdf)).isZero();
    }
}
