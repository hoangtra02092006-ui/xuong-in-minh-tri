package vn.minhtri.xuongin.export.tif;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.minhtri.xuongin.raster.BandConsumer;

/**
 * Streams one sheet into a print-ready TIF (spec 10.2 to 10.4): CMYK + W1 white ink, 8 bits,
 * ExtraSamples 0, resolution tags, ICC profile, Photoshop spot-channel block (34377) and
 * transparency layer (37724). Without W1 ({@code APP_TIFF_WHITE_ENABLED=false}) the file is plain
 * CMYK; in RGB mode ({@code APP_TIFF_COLOR_MODE=rgb}) it is RGB with the sRGB profile, no W1.
 *
 * <p>Usage: get one from {@link TifEncoderFactory#open}, pass it to the rasterizer as a
 * {@link BandConsumer} (bands of any height, top to bottom), then {@link #finish()}. Always
 * {@link #close()} (try-with-resources): closing an unfinished encoder deletes the partial file,
 * so a sheet with missing metadata is never published.
 *
 * <p>Memory: only the caller's whole-sheet 1-byte ink mask is sheet-sized. The encoder converts
 * colour {@value #CHUNK_ROWS} rows at a time, keeps one strip of 5-channel samples, and streams
 * the layer channels to temp files next to the target. Output is byte-identical whatever band
 * heights the rasterizer uses.
 */
public final class TifEncoder implements BandConsumer, Closeable {

    static final int ROWS_PER_STRIP = 64;
    static final int SAMPLES = 5;
    static final int CMYK = 4;
    /** W1 value where white ink prints; Photoshop stores spot channels inverted (spec 10.3). */
    static final byte W1_INK = 0;
    static final byte W1_NONE = (byte) 0xFF;

    static final int TAG_IMAGE_WIDTH = 256;
    static final int TAG_IMAGE_LENGTH = 257;
    static final int TAG_BITS_PER_SAMPLE = 258;
    static final int TAG_COMPRESSION = 259;
    static final int TAG_PHOTOMETRIC = 262;
    static final int TAG_STRIP_OFFSETS = 273;
    static final int TAG_SAMPLES_PER_PIXEL = 277;
    static final int TAG_ROWS_PER_STRIP = 278;
    static final int TAG_STRIP_BYTE_COUNTS = 279;
    static final int TAG_X_RESOLUTION = 282;
    static final int TAG_Y_RESOLUTION = 283;
    static final int TAG_PLANAR_CONFIG = 284;
    static final int TAG_RESOLUTION_UNIT = 296;
    static final int TAG_SOFTWARE = 305;
    static final int TAG_EXTRA_SAMPLES = 338;
    static final int TAG_PHOTOSHOP = 34377;
    static final int TAG_ICC_PROFILE = 34675;
    static final int TAG_IMAGE_SOURCE_DATA = 37724;

    static final int PHOTOMETRIC_RGB = 2;
    static final int PHOTOMETRIC_SEPARATED = 5;
    static final int RGB = 3;
    static final int RESOLUTION_UNIT_INCH = 2;
    static final int EXTRA_SAMPLES_UNSPECIFIED = 0;
    static final String SOFTWARE = "Xuong in Minh Tri";

    private static final int CHUNK_ROWS = 64;
    private static final Logger log = LoggerFactory.getLogger(TifEncoder.class);

    /**
     * Everything the encoder needs besides the sheet itself.
     *
     * @param converter    RGB to CMYK; unused (may be null) in RGB mode
     * @param iccProfile   bytes for tag 34675: the CMYK profile, or sRGB in RGB mode
     * @param whiteEnabled W1 channel; always off in RGB mode
     */
    record Options(CmykConverter converter, byte[] iccProfile, String whiteChannelName, int chokePixels,
            TifCompression compression, double compressionQuality, boolean transparentLayer, boolean layerZip,
            TifColorMode colorMode, boolean whiteEnabled) {

        /** CMYK + W1, the print shop's default. */
        Options(CmykConverter converter, byte[] iccProfile, String whiteChannelName, int chokePixels,
                TifCompression compression, double compressionQuality, boolean transparentLayer, boolean layerZip) {
            this(converter, iccProfile, whiteChannelName, chokePixels, compression, compressionQuality,
                    transparentLayer, layerZip, TifColorMode.CMYK, true);
        }

        boolean rgb() {
            return colorMode == TifColorMode.RGB;
        }

        /** W1 is written only in CMYK mode and when enabled. */
        boolean white() {
            return whiteEnabled && !rgb();
        }
    }

    private final Path target;
    private final Path part;
    private final int width;
    private final int height;
    private final int dpi;
    private final byte[] mask;
    private final Options options;
    private final WhiteChoke choke;
    private final StripCodec codec;
    private final TiffFile file;
    private final PhotoshopLayer layer;

    private final int colours;
    private final int samples;
    private final int[] rgb;
    private final byte[] colour;
    private final boolean[] white;
    private final byte[] strip;
    private final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    private final long[] stripOffsets;
    private final long[] stripByteCounts;
    private final long startedAt = System.nanoTime();

    private int nextRow;
    private int rowsInStrip;
    private int stripIndex;
    private boolean finished;
    private boolean closed;

    /**
     * @param inkMask whole-sheet mask, row-major {@code width × height}, non-zero = something
     *                prints ("có hình", not yet contracted). It drives the layer transparency as
     *                is and W1 after contracting by {@link Options#chokePixels()}
     */
    TifEncoder(Path target, int width, int height, int dpi, byte[] inkMask, Options options) throws IOException {
        if (width <= 0 || height <= 0 || dpi <= 0) {
            throw new IllegalArgumentException("width, height and dpi must be positive");
        }
        if ((long) width * height * SAMPLES > Integer.MAX_VALUE || inkMask.length != (long) width * height) {
            throw new IllegalArgumentException("ink mask must hold width × height = " + (long) width * height
                    + " bytes, got " + inkMask.length);
        }
        this.target = target.toAbsolutePath();
        this.part = this.target.resolveSibling(this.target.getFileName() + ".part");
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.mask = inkMask;
        this.options = options;
        this.colours = options.rgb() ? RGB : CMYK;
        this.samples = colours + (options.white() ? 1 : 0);
        this.choke = options.white() ? new WhiteChoke(inkMask, width, height, options.chokePixels()) : null;
        this.rgb = new int[CHUNK_ROWS * width];
        this.colour = new byte[CHUNK_ROWS * width * colours];
        this.white = options.white() ? new boolean[width] : null;
        this.strip = new byte[ROWS_PER_STRIP * width * samples];
        int strips = (height + ROWS_PER_STRIP - 1) / ROWS_PER_STRIP;
        this.stripOffsets = new long[strips];
        this.stripByteCounts = new long[strips];

        this.codec = StripCodec.of(options.compression(), options.compressionQuality());
        TiffFile opened = null;
        try {
            opened = new TiffFile(part);
            this.layer = options.transparentLayer()
                    ? new PhotoshopLayer(this.target.getParent(), width, height, options.layerZip(), colours,
                            !options.rgb())
                    : null;
        } catch (IOException | RuntimeException e) {
            codec.close();
            if (opened != null) {
                opened.close();
            }
            Files.deleteIfExists(part);
            throw e;
        }
        this.file = opened;
    }

    /**
     * @param argb {@code rows × width} pixels, row-major from index 0, non-premultiplied ARGB
     *             (TYPE_INT_ARGB). Transparent pixels are composited onto white before colour
     *             conversion, so empty areas carry no ink
     */
    @Override
    public void accept(int y, int rows, int bandWidth, int[] argb) throws IOException {
        if (finished || closed) {
            throw new IllegalStateException("encoder already finished or closed");
        }
        if (y != nextRow || rows <= 0 || y + rows > height) {
            throw new IllegalArgumentException("expected rows from " + nextRow + " within height " + height
                    + ", got y=" + y + " rows=" + rows);
        }
        if (bandWidth != width || argb.length < rows * width) {
            throw new IllegalArgumentException("band must be " + width + " px wide with " + rows * width
                    + " pixels, got width " + bandWidth + " and " + argb.length + " pixels");
        }
        for (int done = 0; done < rows; done += CHUNK_ROWS) {
            int n = Math.min(CHUNK_ROWS, rows - done);
            flattenOntoWhite(argb, done * width, n * width);
            if (options.rgb()) {
                splitRgb(n * width);
            } else {
                options.converter().convert(rgb, width, n, colour);
            }
            for (int r = 0; r < n; r++) {
                emitRow(r * width * colours);
            }
        }
    }

    /** Writes metadata, moves the file into place and returns it. Fails rather than publish a TIF missing tags. */
    public TifResult finish() throws IOException {
        if (closed || finished) {
            throw new IllegalStateException("encoder already finished or closed");
        }
        if (nextRow != height) {
            throw new IllegalStateException("received " + nextRow + " of " + height + " rows");
        }
        if (layer != null) {
            file.align();
            long offset = file.position();
            long length = layer.writeTo(file);
            file.addWritten(TAG_IMAGE_SOURCE_DATA, TiffFile.TYPE_UNDEFINED, length, offset);
        }
        file.addLongs(TAG_IMAGE_WIDTH, width);
        file.addLongs(TAG_IMAGE_LENGTH, height);
        int[] bits = new int[samples];
        Arrays.fill(bits, 8);
        file.addShorts(TAG_BITS_PER_SAMPLE, bits);
        file.addShorts(TAG_COMPRESSION, codec.tiffCode());
        file.addShorts(TAG_PHOTOMETRIC, options.rgb() ? PHOTOMETRIC_RGB : PHOTOMETRIC_SEPARATED);
        file.addLongs(TAG_STRIP_OFFSETS, stripOffsets);
        file.addShorts(TAG_SAMPLES_PER_PIXEL, samples);
        file.addLongs(TAG_ROWS_PER_STRIP, ROWS_PER_STRIP);
        file.addLongs(TAG_STRIP_BYTE_COUNTS, stripByteCounts);
        file.addRational(TAG_X_RESOLUTION, dpi, 1);
        file.addRational(TAG_Y_RESOLUTION, dpi, 1);
        file.addShorts(TAG_PLANAR_CONFIG, 1);
        file.addShorts(TAG_RESOLUTION_UNIT, RESOLUTION_UNIT_INCH);
        file.addAscii(TAG_SOFTWARE, SOFTWARE);
        if (options.white()) {
            // 0 = unspecified. 2 (unassociated alpha) would make software read W1 as transparency.
            file.addShorts(TAG_EXTRA_SAMPLES, EXTRA_SAMPLES_UNSPECIFIED);
            file.addBytes(TAG_PHOTOSHOP, PhotoshopResources.spotChannel(options.whiteChannelName()));
        }
        file.addBytes(TAG_ICC_PROFILE, options.iccProfile());
        file.finish();
        file.close();
        releaseHelpers();
        moveIntoPlace();
        finished = true;

        long size = Files.size(target);
        log.info("TIF written: {} x {} px at {} DPI, {} KB, {} ms", width, height, dpi, size / 1024,
                (System.nanoTime() - startedAt) / 1_000_000);
        return new TifResult(target, size, width, height, dpi);
    }

    /** Releases resources; if {@link #finish()} did not complete, deletes the partial file. */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (finished) {
            return;
        }
        try {
            file.close();
            releaseHelpers();
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private void releaseHelpers() throws IOException {
        codec.close();
        if (layer != null) {
            layer.close();
        }
    }

    private void moveIntoPlace() throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Non-premultiplied "over white"; must happen before conversion so alpha never reaches the CMM. */
    private void flattenOntoWhite(int[] argb, int from, int count) {
        for (int i = 0; i < count; i++) {
            int p = argb[from + i];
            int a = p >>> 24;
            if (a == 0xFF) {
                rgb[i] = p;
                continue;
            }
            int rest = 255 * (255 - a);
            int r = (((p >> 16) & 0xFF) * a + rest) / 255;
            int g = (((p >> 8) & 0xFF) * a + rest) / 255;
            int b = ((p & 0xFF) * a + rest) / 255;
            rgb[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    /** RGB mode: the flattened pixels as interleaved R, G, B bytes. */
    private void splitRgb(int count) {
        for (int i = 0, at = 0; i < count; i++, at += RGB) {
            int p = rgb[i];
            colour[at] = (byte) (p >> 16);
            colour[at + 1] = (byte) (p >> 8);
            colour[at + 2] = (byte) p;
        }
    }

    private void emitRow(int colourOffset) throws IOException {
        int at = rowsInStrip * width * samples;
        if (white == null) {
            System.arraycopy(colour, colourOffset, strip, at, width * colours);
        } else {
            choke.row(nextRow, white);
            for (int x = 0, c = colourOffset; x < width; x++, c += CMYK, at += samples) {
                strip[at] = colour[c];
                strip[at + 1] = colour[c + 1];
                strip[at + 2] = colour[c + 2];
                strip[at + 3] = colour[c + 3];
                strip[at + 4] = white[x] ? W1_INK : W1_NONE;
            }
        }
        if (layer != null) {
            layer.addRow(colour, colourOffset, mask, nextRow * width);
        }
        nextRow++;
        rowsInStrip++;
        if (rowsInStrip == ROWS_PER_STRIP || nextRow == height) {
            writeStrip();
        }
    }

    private void writeStrip() throws IOException {
        compressed.reset();
        codec.compress(strip, rowsInStrip * width * samples, compressed);
        file.align();
        stripOffsets[stripIndex] = file.position();
        stripByteCounts[stripIndex] = compressed.size();
        file.write(compressed.toByteArray());
        stripIndex++;
        rowsInStrip = 0;
    }
}
