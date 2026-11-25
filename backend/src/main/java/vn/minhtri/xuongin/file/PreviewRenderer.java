package vn.minhtri.xuongin.file;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/**
 * Small preview images for the upload table (spec 6.2). Display only: export always reads the source file.
 * PDF: PNG cropped to the stored content box (so it matches widthMm/heightMm), /Rotate applied once by the renderer.
 * Image: JPEG of the whole picture, decoded with subsampling so a large photo never sits in the heap at full size.
 */
@Component
public class PreviewRenderer {

    static final float MIN_DPI = 24;
    static final float MAX_DPI = 200;
    private static final float POINTS_PER_INCH = 72;

    private final FileProperties props;

    public PreviewRenderer(FileProperties props) {
        this.props = props;
    }

    byte[] render(StoredFile file, Path source) throws IOException {
        return file.type() == FileType.PDF ? renderPdf(file.contentBox(), source) : renderImage(source);
    }

    byte[] renderPdf(ContentBox box, Path pdf) throws IOException {
        try (PDDocument doc = PdfMetadataReader.open(pdf)) {
            PDPage page = doc.getPage(0);
            // Cropping the page (not the rendered bitmap) lets the renderer handle /Rotate exactly once.
            page.setCropBox(new PDRectangle((float) box.xPt(), (float) box.yPt(), (float) box.widthPt(), (float) box.heightPt()));
            double longSidePt = Math.max(box.widthPt(), box.heightPt());
            float dpi = (float) (props.getPreviewMaxPx() * POINTS_PER_INCH / longSidePt);
            dpi = Math.max(MIN_DPI, Math.min(MAX_DPI, dpi));
            BufferedImage image = new PDFRenderer(doc).renderImage(0, dpi / POINTS_PER_INCH, ImageType.ARGB);
            BufferedImage fitted = fit(image, BufferedImage.TYPE_INT_ARGB, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(fitted, "png", out);
            return out.toByteArray();
        }
    }

    byte[] renderImage(Path file) throws IOException {
        BufferedImage decoded;
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = ImageMetadataReader.readerFor(in);
            try {
                reader.setInput(in, true, true);
                int longSide = Math.max(reader.getWidth(0), reader.getHeight(0));
                // Keep up to 2x the target size so the final smooth downscale still looks clean.
                int step = Math.max(1, longSide / (props.getPreviewMaxPx() * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                decoded = reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
        // JPEG has no alpha: transparent areas are shown on white, as on the roll.
        BufferedImage fitted = fit(decoded, BufferedImage.TYPE_INT_RGB, Color.WHITE);
        return writeJpeg(fitted, props.getPreviewJpegQuality());
    }

    /** Scales so the long side is at most previewMaxPx (never enlarges) into an image of the given type. */
    private BufferedImage fit(BufferedImage src, int type, Color background) {
        int max = props.getPreviewMaxPx();
        double scale = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
        if (scale == 1.0 && src.getType() == type && background == null) {
            return src;
        }
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        try {
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, w, h);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
