package vn.minhtri.xuongin.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import vn.minhtri.xuongin.shared.Units;

/**
 * Reads PNG / JPG physical size and transparency from the header only, without decoding pixels (spec 7.2).
 * Images are never trimmed: a white border on a decal can be intentional.
 */
@Component
public class ImageMetadataReader {

    private static final double MM_PER_INCH = 25.4;

    private final FileProperties props;

    public ImageMetadataReader(FileProperties props) {
        this.props = props;
    }

    SourceMetadata read(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = readerFor(in);
            try {
                reader.setInput(in, true, false);
                int widthPx = reader.getWidth(0);
                int heightPx = reader.getHeight(0);
                if (widthPx <= 0 || heightPx <= 0) {
                    throw new IOException("Image has no pixels");
                }
                IIOMetadata meta = metadata(reader);
                Node standard = standardTree(meta);
                double dpiX = dpi(standard, "HorizontalPixelSize");
                double dpiY = dpi(standard, "VerticalPixelSize");
                if (Double.isNaN(dpiX)) {
                    dpiX = Double.isNaN(dpiY) ? props.getDefaultImageDpi() : dpiY;
                }
                if (Double.isNaN(dpiY)) {
                    dpiY = dpiX;
                }
                double widthMm = widthPx * MM_PER_INCH / dpiX;
                double heightMm = heightPx * MM_PER_INCH / dpiY;
                ContentBox box = new ContentBox(0, 0, Units.mmToPt(widthMm), Units.mmToPt(heightMm));
                return new SourceMetadata(FileType.IMAGE, widthMm, heightMm, widthMm, heightMm,
                        box, null, 0, 1, hasAlpha(reader, standard));
            } finally {
                reader.dispose();
            }
        }
    }

    /** First ImageIO reader that recognises the bytes (not the extension). */
    static ImageReader readerFor(ImageInputStream in) throws IOException {
        if (in == null) {
            throw new IOException("Cannot open image");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
        if (!readers.hasNext()) {
            throw new IOException("Unknown image format");
        }
        return readers.next();
    }

    private static IIOMetadata metadata(ImageReader reader) {
        try {
            return reader.getImageMetadata(0);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Node standardTree(IIOMetadata meta) {
        if (meta == null || !meta.isStandardMetadataFormatSupported()) {
            return null;
        }
        return meta.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
    }

    /** DPI from the standard metadata (pixel size is mm per pixel), or NaN when not declared. */
    static double dpi(Node standard, String sizeNode) {
        Node node = find(find(standard, "Dimension"), sizeNode);
        String value = attribute(node, "value");
        if (value == null) {
            return Double.NaN;
        }
        try {
            double mmPerPixel = Double.parseDouble(value);
            return mmPerPixel > 0 && Double.isFinite(mmPerPixel) ? MM_PER_INCH / mmPerPixel : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Transparency from the declared image type and the tRNS-style metadata. When it cannot be told,
     * answer true: extra white ink under artwork is better than missing white text (spec 7.2, 12).
     */
    static boolean hasAlpha(ImageReader reader, Node standard) {
        Node transparency = find(standard, "Transparency");
        if (transparency != null) {
            String alpha = attribute(find(transparency, "Alpha"), "value");
            if (alpha != null && !"none".equals(alpha)) {
                return true;
            }
            if (find(transparency, "TransparentIndex") != null || find(transparency, "TransparentColor") != null) {
                return true;
            }
        }
        try {
            ImageTypeSpecifier raw = reader.getRawImageType(0);
            if (raw == null) {
                Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
                raw = types.hasNext() ? types.next() : null;
            }
            return raw == null || raw.getColorModel().hasAlpha();
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    private static Node find(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static String attribute(Node node, String name) {
        if (node == null || node.getAttributes() == null) {
            return null;
        }
        Node attr = node.getAttributes().getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }
}
