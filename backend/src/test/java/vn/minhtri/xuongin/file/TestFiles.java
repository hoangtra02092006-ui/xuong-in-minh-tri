package vn.minhtri.xuongin.file;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Test fixtures: the shared samples/ folder and small PDFs / images built on the fly. */
final class TestFiles {

    /** mm to PDF points, written out here on purpose so tests do not depend on the code under test. */
    static final double PT = 72.0 / 25.4;

    private TestFiles() {}

    static Path samples() {
        Path fromBackend = Path.of("..", "samples");
        return Files.isDirectory(fromBackend) ? fromBackend : Path.of("samples");
    }

    static Path sample(String name) {
        return samples().resolve(name);
    }

    @FunctionalInterface
    interface Drawing {
        void draw(PDPageContentStream cs, PDDocument doc) throws IOException;
    }

    /** One-page PDF of {@code wMm x hMm} with the given drawing, written to {@code out}. */
    static Path pdf(Path out, double wMm, double hMm, Drawing drawing) throws IOException {
        return pdf(out, wMm, hMm, 0, 1, drawing);
    }

    static Path pdf(Path out, double wMm, double hMm, int rotation, int pages, Drawing drawing) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(new PDRectangle((float) (wMm * PT), (float) (hMm * PT)));
                page.setRotation(rotation);
                doc.addPage(page);
                if (i == 0 && drawing != null) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        drawing.draw(cs, doc);
                    }
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    /** Filled rectangle given in mm from the page's bottom-left corner. */
    static void rect(PDPageContentStream cs, double xMm, double yMm, double wMm, double hMm) throws IOException {
        cs.addRect((float) (xMm * PT), (float) (yMm * PT), (float) (wMm * PT), (float) (hMm * PT));
        cs.fill();
    }

    static byte[] png(int w, int h, int type) throws IOException {
        BufferedImage img = new BufferedImage(w, h, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
