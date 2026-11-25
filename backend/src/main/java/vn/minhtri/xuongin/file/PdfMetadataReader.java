package vn.minhtri.xuongin.file;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.shared.Units;

/**
 * Reads a PDF's first page (spec 7.1): content box from the content stream, falling back to the
 * TrimBox, then CropBox, then MediaBox; page rotation, page count,
 * and the occupancy mask. The content box stored here is the one export must draw with (invariant 9).
 */
@Component
public class PdfMetadataReader {

    /** Content boxes thinner than this on one side are treated as unreadable (spec 7.1 step 3). */
    static final double MIN_CONTENT_MM = 1.0;

    private static final Logger log = LoggerFactory.getLogger(PdfMetadataReader.class);

    private final FileProperties props;
    private final OccupancyMaskBuilder maskBuilder;

    public PdfMetadataReader(FileProperties props) {
        this.props = props;
        this.maskBuilder = new OccupancyMaskBuilder(props.getMaskPixelsPerCell(), props.getMaskBandPixels(), props.getMaskMaxCells());
    }

    /** Opens a PDF with a temp-file stream cache so large files do not sit in the heap. */
    static PDDocument open(Path pdf) throws IOException {
        return Loader.loadPDF(pdf.toFile(), IOUtils.createTempFileOnlyStreamCache());
    }

    SourceMetadata read(Path pdf) throws IOException {
        try (PDDocument doc = open(pdf)) {
            int pageCount = doc.getNumberOfPages();
            if (pageCount == 0) {
                throw new IOException("PDF has no pages");
            }
            PDPage page = doc.getPage(0);
            int rotation = normalizeRotation(page.getRotation());
            Rectangle2D pageBox = toRect(pageBox(page));
            if (pageBox.getWidth() <= 0 || pageBox.getHeight() <= 0) {
                throw new IOException("PDF page box is empty");
            }
            Rectangle2D content = props.isTrimEnabled() ? inkBounds(page, pageBox) : null;
            if (content == null) {
                content = pageBox;
            }
            ContentBox box = new ContentBox(content.getX(), content.getY(), content.getWidth(), content.getHeight());
            OccupancyMask mask = buildMask(doc, box);

            boolean quarterTurn = rotation == 90 || rotation == 270;
            double contentW = Units.ptToMm(content.getWidth());
            double contentH = Units.ptToMm(content.getHeight());
            double pageW = Units.ptToMm(pageBox.getWidth());
            double pageH = Units.ptToMm(pageBox.getHeight());
            return new SourceMetadata(
                    FileType.PDF,
                    quarterTurn ? contentH : contentW,
                    quarterTurn ? contentW : contentH,
                    quarterTurn ? pageH : pageW,
                    quarterTurn ? pageW : pageH,
                    box,
                    mask,
                    rotation,
                    pageCount,
                    true);
        }
    }

    /** Ink bounds clipped to the page box, or null when blank, unreadable, too complex or under 1 mm. */
    private Rectangle2D inkBounds(PDPage page, Rectangle2D pageBox) {
        Rectangle2D ink;
        try {
            ink = new PdfInkBoundsFinder(page, props.getMaxShapes()).find();
        } catch (PdfInkBoundsFinder.TooComplexException e) {
            log.info("PDF has more than {} drawing operations, using the page box", props.getMaxShapes());
            return null;
        } catch (IOException | RuntimeException e) {
            log.info("Could not read PDF content stream, using the page box: {}", e.toString());
            return null;
        }
        if (ink == null) {
            return null;
        }
        Rectangle2D clipped = ink.createIntersection(pageBox);
        double minPt = Units.mmToPt(MIN_CONTENT_MM);
        if (clipped.getWidth() < minPt || clipped.getHeight() < minPt) {
            return null;
        }
        return clipped;
    }

    private OccupancyMask buildMask(PDDocument doc, ContentBox box) {
        try {
            return maskBuilder.build(doc, box, OccupancyMask.DEFAULT_CELL_MM);
        } catch (IOException | RuntimeException e) {
            // No mask = solid block: worse packing, never wrong.
            log.warn("Could not build occupancy mask, item will be treated as solid: {}", e.toString());
            return null;
        }
    }

    /** TrimBox, else CropBox, else MediaBox (PDFBox 3 returns null for a missing TrimBox). */
    static PDRectangle pageBox(PDPage page) {
        PDRectangle trim = page.getTrimBox();
        if (trim != null) {
            return trim;
        }
        PDRectangle crop = page.getCropBox();
        return crop != null ? crop : page.getMediaBox();
    }

    static int normalizeRotation(int rotation) {
        int r = ((rotation % 360) + 360) % 360;
        return (int) (Math.round(r / 90.0) * 90) % 360;
    }

    private static Rectangle2D toRect(PDRectangle r) {
        return new Rectangle2D.Double(r.getLowerLeftX(), r.getLowerLeftY(), r.getWidth(), r.getHeight());
    }
}
