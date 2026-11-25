package vn.minhtri.xuongin.raster;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;

/**
 * Heap needed to decode the largest source image of a sheet. Composing the sheet PDF and drawing
 * every band decode each image at its native resolution, whatever the output DPI, and PDFBox
 * holds several copies while doing it (filter output, sample raster, RGB image). Measured on a
 * 6000 × 10000 px photo (60 MP): one band needs about 600 MB of heap for the PNG and 700 MB for
 * the JPG, i.e. about {@value #IMAGE_BYTES_PER_PIXEL} bytes per source pixel. Only one image is
 * decoded at a time, so the largest one counts.
 *
 * <p>Reads image headers and {@code /Width} / {@code /Height} entries only, never the image data.
 */
public final class SourceImages {

    /** Peak decode cost per source pixel (measured, see above). */
    static final int IMAGE_BYTES_PER_PIXEL = 11;
    /** A soft mask (PNG alpha): decoded gray samples and raster, then merged into an ARGB copy. */
    static final int SMASK_BYTES_PER_PIXEL = 6;

    private SourceImages() {}

    /** Bytes to decode a PNG / JPG source file; {@code alpha} adds its soft mask. 0 when unreadable. */
    public static long imageFileBytes(Path image, boolean alpha) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(image.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                return pixels * (IMAGE_BYTES_PER_PIXEL + (alpha ? SMASK_BYTES_PER_PIXEL : 0));
            } finally {
                reader.dispose();
            }
        }
    }

    /** Bytes to decode the largest image on page 1 (form XObjects included); 0 when there is none. */
    public static long largestDecodeBytes(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (document.getNumberOfPages() == 0) {
                return 0;
            }
            Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            return largest(document.getPage(0).getResources(), seen);
        }
    }

    private static long largest(PDResources resources, Set<COSBase> seen) {
        if (resources == null) {
            return 0;
        }
        COSDictionary xobjects = resources.getCOSObject().getCOSDictionary(COSName.XOBJECT);
        if (xobjects == null) {
            return 0;
        }
        long max = 0;
        for (COSName name : xobjects.keySet()) {
            COSBase base = xobjects.getDictionaryObject(name);
            if (!(base instanceof COSStream stream) || !seen.add(stream)) {
                continue;
            }
            COSName subtype = stream.getCOSName(COSName.SUBTYPE);
            if (COSName.IMAGE.equals(subtype)) {
                max = Math.max(max, imageBytes(stream));
            } else if (COSName.FORM.equals(subtype)) {
                COSDictionary inner = stream.getCOSDictionary(COSName.RESOURCES);
                max = Math.max(max, largest(inner == null ? null : new PDResources(inner), seen));
            }
        }
        return max;
    }

    static long imageBytes(COSDictionary image) {
        long bytes = pixels(image) * IMAGE_BYTES_PER_PIXEL;
        COSBase mask = image.getDictionaryObject(COSName.SMASK);
        if (mask instanceof COSDictionary smask) {
            bytes += pixels(smask) * SMASK_BYTES_PER_PIXEL;
        }
        return bytes;
    }

    private static long pixels(COSDictionary image) {
        long width = Math.max(image.getInt(COSName.WIDTH, 0), 0);
        long height = Math.max(image.getInt(COSName.HEIGHT, 0), 0);
        return width * height;
    }
}
