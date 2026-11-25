package vn.minhtri.xuongin.export.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.Units;

/** Builds source files, stored-file records and composed sheets for tests. */
public final class SheetFixtures {

    public static final Path SAMPLES = Path.of("..", "samples");

    private final Map<String, StoredFile> files = new HashMap<>();
    private final Path dir;

    public SheetFixtures(Path dir) {
        this.dir = dir;
    }

    public StoredFile get(String id) {
        return files.get(id);
    }

    public static Path pathOf(StoredFile file) {
        return Path.of(file.storedPath());
    }

    /** Registers a PDF whose content box is the whole page (sizes in mm). */
    public StoredFile pdf(String id, Path path, double widthMm, double heightMm) {
        ContentBox box = new ContentBox(0, 0, Units.mmToPt(widthMm), Units.mmToPt(heightMm));
        return pdf(id, path, box, 0, widthMm, heightMm);
    }

    /** Registers a PDF with an explicit stored content box (points) and /Rotate. */
    public StoredFile pdf(String id, Path path, ContentBox box, int rotation, double sourceW, double sourceH) {
        boolean swapped = rotation == 90 || rotation == 270;
        double w = Units.ptToMm(swapped ? box.heightPt() : box.widthPt());
        double h = Units.ptToMm(swapped ? box.widthPt() : box.heightPt());
        StoredFile file = new StoredFile(id, id + ".pdf", path.toString(), FileType.PDF,
                w, h, sourceW, sourceH, box, null, rotation, 1, true, 1, null);
        files.put(id, file);
        return file;
    }

    public StoredFile image(String id, Path path, double widthMm, double heightMm, boolean hasAlpha) {
        StoredFile file = new StoredFile(id, path.getFileName().toString(), path.toString(), FileType.IMAGE,
                widthMm, heightMm, widthMm, heightMm,
                new ContentBox(0, 0, Units.mmToPt(widthMm), Units.mmToPt(heightMm)),
                null, 0, 1, hasAlpha, 1, null);
        files.put(id, file);
        return file;
    }

    /** Placement at (x, y) of a file, optionally rotated 90 degrees. */
    public Placement place(String id, double x, double y, boolean rotated) {
        StoredFile f = files.get(id);
        double w = rotated ? f.heightMm() : f.widthMm();
        double h = rotated ? f.widthMm() : f.heightMm();
        return new Placement(id, id, 0, x, y, w, h, rotated, 0, f.widthMm(), f.heightMm());
    }

    /** Placement with a free angle (TRUE_SHAPE); w/h = bounding box of the rotated piece. */
    public Placement placeAngled(String id, double x, double y, double angleDeg) {
        StoredFile f = files.get(id);
        double r = Math.toRadians(angleDeg);
        double c = Math.abs(Math.cos(r));
        double s = Math.abs(Math.sin(r));
        double w = f.widthMm() * c + f.heightMm() * s;
        double h = f.widthMm() * s + f.heightMm() * c;
        return new Placement(id, id, 0, x, y, w, h, false, angleDeg, f.widthMm(), f.heightMm());
    }

    public Path compose(String name, double widthMm, double lengthMm, boolean cutLines, List<Placement> placements)
            throws IOException {
        Sheet sheet = new Sheet(0, widthMm, lengthMm, 0, placements);
        Path out = dir.resolve(name + ".pdf");
        try (OutputStream stream = Files.newOutputStream(out)) {
            new PdfComposer().compose(sheet, files::get, SheetFixtures::pathOf, cutLines, stream);
        }
        return out;
    }

    public Sheet sheet(double widthMm, double lengthMm, List<Placement> placements) {
        return new Sheet(0, widthMm, lengthMm, 0, placements);
    }

    /**
     * One-page PDF of {@code w × h} mm with optional /Rotate: solid colour, plus a red square
     * of {@code markMm} at the raw bottom-left corner (0 = none).
     */
    public Path solidPdf(String name, double w, double h, int rotation, float[] rgb, double markMm)
            throws IOException {
        Path out = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle((float) Units.mmToPt(w), (float) Units.mmToPt(h)));
            page.setRotation(rotation);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                cs.addRect(0, 0, (float) Units.mmToPt(w), (float) Units.mmToPt(h));
                cs.fill();
                if (markMm > 0) {
                    cs.setNonStrokingColor(1f, 0f, 0f);
                    cs.addRect(0, 0, (float) Units.mmToPt(markMm), (float) Units.mmToPt(markMm));
                    cs.fill();
                }
            }
            doc.save(out.toFile());
        }
        return out;
    }

    /** Transparent page with white text and a white filled bar: no colour around them at all. */
    public Path whiteArtworkPdf(String name, double w, double h) throws IOException {
        Path out = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle((float) Units.mmToPt(w), (float) Units.mmToPt(h)));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                cs.newLineAtOffset(4, (float) Units.mmToPt(h) / 2);
                cs.showText("BO TUOI");
                cs.endText();
                cs.addRect(4, 4, (float) Units.mmToPt(w) - 8, 6);
                cs.fill();
            }
            doc.save(out.toFile());
        }
        return out;
    }

    /** Writes an image file (png / jpg by extension). */
    public Path imageFile(String name, BufferedImage image) throws IOException {
        Path out = dir.resolve(name);
        String format = name.endsWith(".png") ? "png" : "jpg";
        if (!ImageIO.write(image, format, out.toFile())) {
            throw new IOException("no writer for " + format);
        }
        return out;
    }
}
