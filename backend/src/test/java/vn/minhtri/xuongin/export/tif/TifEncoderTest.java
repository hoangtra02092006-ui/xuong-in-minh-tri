package vn.minhtri.xuongin.export.tif;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.minhtri.xuongin.export.tif.TiffTestReader.Layer;
import vn.minhtri.xuongin.export.tif.TiffTestReader.LayerChannel;
import vn.minhtri.xuongin.export.tif.TiffTestReader.Resource;
import vn.minhtri.xuongin.export.tif.TiffTestReader.Tiff;

class TifEncoderTest {

    static final int W = 200;
    static final int H = 120;
    static final int DPI = 300;
    static final byte[] FAKE_ICC = "fake-icc-profile-bytes".getBytes(StandardCharsets.US_ASCII);

    /** Deterministic stand-in for the ICC converter so CI covers every non-colour path. */
    static final CmykConverter NAIVE = (rgb, width, rows, cmyk) -> {
        for (int i = 0; i < width * rows; i++) {
            int p = rgb[i];
            cmyk[i * 4] = (byte) (255 - ((p >> 16) & 0xFF));
            cmyk[i * 4 + 1] = (byte) (255 - ((p >> 8) & 0xFF));
            cmyk[i * 4 + 2] = (byte) (255 - (p & 0xFF));
            cmyk[i * 4 + 3] = (byte) ((p ^ (p >> 8) ^ (p >> 16)) & 0x3F);
        }
    };

    @TempDir
    Path dir;

    /**
     * 200 × 120 sheet: transparent background, opaque red block, opaque WHITE block (white artwork
     * must still get white ink), a half-transparent blue block below the alpha threshold (no ink)
     * and a green block touching the left and bottom edges.
     */
    static int[] sheet() {
        int[] argb = new int[W * H];
        fill(argb, 20, 10, 80, 60, 0xFFFF0000);
        fill(argb, 100, 30, 150, 90, 0xFFFFFFFF);
        fill(argb, 160, 70, 190, 110, 0x640000FF);
        fill(argb, 0, 100, 10, 120, 0xFF00FF00);
        return argb;
    }

    static void fill(int[] argb, int x0, int y0, int x1, int y1, int colour) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                argb[y * W + x] = colour;
            }
        }
    }

    /** What the raster module provides: alpha above 128 = something prints. */
    static byte[] maskOf(int[] argb) {
        byte[] mask = new byte[argb.length];
        for (int i = 0; i < argb.length; i++) {
            mask[i] = (byte) ((argb[i] >>> 24) > 128 ? 1 : 0);
        }
        return mask;
    }

    static int count(byte[] mask) {
        int n = 0;
        for (byte b : mask) {
            n += b != 0 ? 1 : 0;
        }
        return n;
    }

    static TifEncoder.Options options(int choke, TifCompression compression, boolean layer, boolean zip) {
        return new TifEncoder.Options(NAIVE, FAKE_ICC, "W1", choke, compression, 0.3, layer, zip);
    }

    static TifEncoder.Options defaults() {
        return options(0, TifCompression.DEFLATE, true, true);
    }

    Path encode(String name, int bandRows, TifEncoder.Options options) throws IOException {
        int[] argb = sheet();
        Path target = dir.resolve(name);
        try (TifEncoder encoder = new TifEncoder(target, W, H, DPI, maskOf(argb), options)) {
            for (int y = 0; y < H; y += bandRows) {
                int rows = Math.min(bandRows, H - y);
                int[] band = new int[rows * W];
                System.arraycopy(argb, y * W, band, 0, rows * W);
                encoder.accept(y, rows, W, band);
            }
            TifResult result = encoder.finish();
            assertThat(result.path()).isEqualTo(target.toAbsolutePath());
            assertThat(result.sizeBytes()).isEqualTo(Files.size(target));
        }
        return target;
    }

    static Tiff read(Path file) throws IOException {
        return TiffTestReader.parse(Files.readAllBytes(file));
    }

    @Test
    void writesTheTagsThePrintShopFileHas() throws IOException {
        Tiff tif = read(encode("a.tif", 50, defaults()));

        assertThat(tif.get(TifEncoder.TAG_IMAGE_WIDTH)).isEqualTo(W);
        assertThat(tif.get(TifEncoder.TAG_IMAGE_LENGTH)).isEqualTo(H);
        assertThat(tif.get(TifEncoder.TAG_SAMPLES_PER_PIXEL)).isEqualTo(5);
        assertThat(tif.all(TifEncoder.TAG_BITS_PER_SAMPLE)).containsExactly(8, 8, 8, 8, 8);
        assertThat(tif.get(TifEncoder.TAG_PHOTOMETRIC)).isEqualTo(5);
        assertThat(tif.all(TifEncoder.TAG_EXTRA_SAMPLES)).containsExactly(0);
        assertThat(tif.get(TifEncoder.TAG_COMPRESSION)).isEqualTo(8);
        assertThat(tif.get(TifEncoder.TAG_ROWS_PER_STRIP)).isEqualTo(64);
        assertThat(tif.all(TifEncoder.TAG_STRIP_OFFSETS)).hasSize(2);
        assertThat(tif.get(TifEncoder.TAG_PLANAR_CONFIG)).isEqualTo(1);
        assertThat(tif.rational(TifEncoder.TAG_X_RESOLUTION)).containsExactly(300, 1);
        assertThat(tif.rational(TifEncoder.TAG_Y_RESOLUTION)).containsExactly(300, 1);
        assertThat(tif.get(TifEncoder.TAG_RESOLUTION_UNIT)).isEqualTo(2);
        assertThat(tif.raw(TifEncoder.TAG_ICC_PROFILE)).isEqualTo(FAKE_ICC);
        assertThat(tif.fields()).containsKeys(TifEncoder.TAG_PHOTOSHOP, TifEncoder.TAG_IMAGE_SOURCE_DATA);
        assertThat(Files.list(dir).map(p -> p.getFileName().toString())).containsExactly("a.tif");
    }

    @Test
    void photoshopBlockDeclaresW1AsSpotColourAtFullSolidity() throws IOException {
        Tiff tif = read(encode("a.tif", 120, defaults()));

        List<Resource> resources = TiffTestReader.resources(tif.raw(TifEncoder.TAG_PHOTOSHOP));
        assertThat(resources).extracting(Resource::id).containsExactly(1006, 1045, 1077, 1053);

        byte[] pascal = resources.get(0).data();
        assertThat(new String(pascal, 1, pascal[0], StandardCharsets.US_ASCII)).isEqualTo("W1");
        byte[] unicode = resources.get(1).data();
        assertThat(java.nio.ByteBuffer.wrap(unicode).getInt()).isEqualTo(3);
        assertThat(new String(unicode, 4, 4, StandardCharsets.UTF_16BE)).isEqualTo("W1");
        byte[] display = resources.get(2).data();
        assertThat(display).hasSize(17);
        assertThat(java.nio.ByteBuffer.wrap(display).getShort(14)).isEqualTo((short) 100);
        assertThat(display[16]).isEqualTo((byte) 2);
    }

    @Test
    void everyW1PixelMatchesTheMaskAndZeroMeansInk() throws IOException {
        int[] argb = sheet();
        byte[] mask = maskOf(argb);
        byte[] samples = read(encode("a.tif", 33, defaults())).samples();
        assertThat(samples).hasSize(W * H * 5);

        int ink = 0;
        for (int i = 0; i < W * H; i++) {
            int w1 = samples[i * 5 + 4] & 0xFF;
            assertThat(w1).isIn(0, 255);
            assertThat(w1 == 0).as("W1 at pixel %d", i).isEqualTo(mask[i] != 0);
            ink += w1 == 0 ? 1 : 0;
        }
        // red 60×50 + white 50×60 + green 10×20; the half-transparent block is not ink
        assertThat(ink).isEqualTo(count(mask)).isEqualTo(3000 + 3000 + 200);
    }

    @Test
    void colourIsConvertedAfterCompositingOntoWhite() throws IOException {
        byte[] samples = read(encode("a.tif", 64, defaults())).samples();

        assertPixel(samples, 0, 0, 0xFFFFFFFF);
        assertPixel(samples, 30, 20, 0xFFFF0000);
        assertPixel(samples, 120, 40, 0xFFFFFFFF);
        // 0x64 alpha blue over white: (255·100 + 255·155)/255 = 255 for blue, (0·100 + 255·155)/255 = 155
        assertPixel(samples, 170, 80, 0xFF9B9BFF);
    }

    static void assertPixel(byte[] samples, int x, int y, int rgb) {
        int i = y * W + x;
        byte[] expected = new byte[4];
        NAIVE.convert(new int[] {rgb}, 1, 1, expected);
        for (int c = 0; c < 4; c++) {
            assertThat(samples[i * 5 + c]).as("channel %d at %d,%d", c, x, y).isEqualTo(expected[c]);
        }
    }

    @Test
    void w1IsContractedOnePixelOnEverySideIncludingTheSheetEdge() throws IOException {
        int[] argb = sheet();
        byte[] mask = maskOf(argb);
        byte[] samples = read(encode("a.tif", 17, options(1, TifCompression.DEFLATE, true, true))).samples();

        byte[] expected = erode(mask, 1);
        int ink = 0;
        for (int i = 0; i < W * H; i++) {
            boolean w1 = samples[i * 5 + 4] == 0;
            assertThat(w1).as("W1 at pixel %d", i).isEqualTo(expected[i] != 0);
            ink += w1 ? 1 : 0;
        }
        // red 58×48 + white 48×58; green touches left and bottom edges: 8 × 18
        assertThat(ink).isEqualTo(count(expected)).isEqualTo(58 * 48 + 48 * 58 + 8 * 18);
    }

    @Test
    void largeChokeRadiusLeavesNoWhite() throws IOException {
        byte[] samples = read(encode("a.tif", 120, options(300, TifCompression.DEFLATE, false, true))).samples();
        for (int i = 0; i < W * H; i++) {
            assertThat(samples[i * 5 + 4]).isEqualTo((byte) 0xFF);
        }
    }

    /** Brute-force square erosion, outside the sheet = no ink. */
    static byte[] erode(byte[] mask, int r) {
        byte[] out = new byte[mask.length];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean ink = true;
                for (int dy = -r; dy <= r && ink; dy++) {
                    for (int dx = -r; dx <= r && ink; dx++) {
                        int yy = y + dy;
                        int xx = x + dx;
                        ink = yy >= 0 && yy < H && xx >= 0 && xx < W && mask[yy * W + xx] != 0;
                    }
                }
                out[y * W + x] = (byte) (ink ? 1 : 0);
            }
        }
        return out;
    }

    // ---- APP_TIFF_WHITE_ENABLED / APP_TIFF_COLOR_MODE (spec 10.2, 13) ----

    static final byte[] SRGB = "srgb-profile".getBytes(StandardCharsets.US_ASCII);

    @Test
    void withoutWhiteTheFileIsPlainCmyk() throws IOException {
        TifEncoder.Options noWhite = new TifEncoder.Options(NAIVE, FAKE_ICC, "W1", 1, TifCompression.DEFLATE, 0.3,
                true, true, TifColorMode.CMYK, false);
        Tiff tif = read(encode("a.tif", 50, noWhite));
        assertThat(tif.get(TifEncoder.TAG_SAMPLES_PER_PIXEL)).isEqualTo(4);
        assertThat(tif.all(TifEncoder.TAG_BITS_PER_SAMPLE)).containsExactly(8, 8, 8, 8);
        assertThat(tif.get(TifEncoder.TAG_PHOTOMETRIC)).isEqualTo(5);
        assertThat(tif.fields()).doesNotContainKeys(TifEncoder.TAG_EXTRA_SAMPLES, TifEncoder.TAG_PHOTOSHOP);
        assertThat(tif.raw(TifEncoder.TAG_ICC_PROFILE)).isEqualTo(FAKE_ICC);

        // Same CMYK values as the default file, just without the fifth channel.
        byte[] four = tif.samples();
        byte[] five = read(encode("b.tif", 50, options(1, TifCompression.DEFLATE, true, true))).samples();
        assertThat(four).hasSize(W * H * 4);
        for (int i = 0; i < W * H; i++) {
            for (int c = 0; c < 4; c++) {
                assertThat(four[i * 4 + c]).isEqualTo(five[i * 5 + c]);
            }
        }
        Layer layer = TiffTestReader.layer(tif.raw(TifEncoder.TAG_IMAGE_SOURCE_DATA), ByteOrder.BIG_ENDIAN);
        assertThat(layer.channels()).extracting(LayerChannel::id).containsExactly(-1, 0, 1, 2, 3);
    }

    @Test
    void rgbModeWritesRgbWithTheSrgbProfileAndNoW1() throws IOException {
        // White enabled in the settings is ignored: spec 10.2 "rgb" has no W1. No CMYK converter needed.
        TifEncoder.Options rgb = new TifEncoder.Options(null, SRGB, "W1", 1, TifCompression.DEFLATE, 0.3,
                true, true, TifColorMode.RGB, true);
        Tiff tif = read(encode("a.tif", 37, rgb));
        assertThat(tif.get(TifEncoder.TAG_SAMPLES_PER_PIXEL)).isEqualTo(3);
        assertThat(tif.all(TifEncoder.TAG_BITS_PER_SAMPLE)).containsExactly(8, 8, 8);
        assertThat(tif.get(TifEncoder.TAG_PHOTOMETRIC)).isEqualTo(2);
        assertThat(tif.fields()).doesNotContainKeys(TifEncoder.TAG_EXTRA_SAMPLES, TifEncoder.TAG_PHOTOSHOP);
        assertThat(tif.raw(TifEncoder.TAG_ICC_PROFILE)).isEqualTo(SRGB);
        assertThat(tif.rational(TifEncoder.TAG_X_RESOLUTION)).containsExactly(300, 1);

        byte[] samples = tif.samples();
        assertThat(samples).hasSize(W * H * 3);
        assertRgb(samples, 0, 0, 0xFFFFFF);      // transparent → white, no ink
        assertRgb(samples, 30, 20, 0xFF0000);    // red
        assertRgb(samples, 170, 80, 0x9B9BFF);   // 0x64 blue over white
        assertRgb(samples, 5, 110, 0x00FF00);    // green at the edge

        // Layer: transparency + R, G, B stored as is (PSD inverts CMYK only).
        Layer layer = TiffTestReader.layer(tif.raw(TifEncoder.TAG_IMAGE_SOURCE_DATA), ByteOrder.BIG_ENDIAN);
        assertThat(layer.channels()).extracting(LayerChannel::id).containsExactly(-1, 0, 1, 2);
        byte[] mask = maskOf(sheet());
        for (int i = 0; i < W * H; i++) {
            assertThat(layer.channels().get(0).decoded()[i]).isEqualTo(mask[i] != 0 ? (byte) 0xFF : 0);
            for (int c = 0; c < 3; c++) {
                assertThat(layer.channels().get(c + 1).decoded()[i]).isEqualTo(samples[i * 3 + c]);
            }
        }
    }

    static void assertRgb(byte[] samples, int x, int y, int rgb) {
        int i = (y * W + x) * 3;
        assertThat(new int[] {samples[i] & 0xFF, samples[i + 1] & 0xFF, samples[i + 2] & 0xFF})
                .as("pixel %d,%d", x, y).containsExactly((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    @Test
    void layerBlockHasOneFullSheetLayerWithTransparencyAndInvertedCmyk() throws IOException {
        int[] argb = sheet();
        byte[] mask = maskOf(argb);
        Tiff tif = read(encode("a.tif", 40, options(1, TifCompression.DEFLATE, true, true)));
        byte[] samples = tif.samples();

        Layer layer = TiffTestReader.layer(tif.raw(TifEncoder.TAG_IMAGE_SOURCE_DATA), ByteOrder.BIG_ENDIAN);
        assertLayer(layer, mask, samples, PhotoshopLayer.COMPRESSION_ZIP);
    }

    @Test
    void rleLayerDecodesToTheSameChannels() throws IOException {
        int[] argb = sheet();
        Tiff tif = read(encode("a.tif", 120, options(0, TifCompression.DEFLATE, true, false)));
        Layer layer = TiffTestReader.layer(tif.raw(TifEncoder.TAG_IMAGE_SOURCE_DATA), ByteOrder.BIG_ENDIAN);
        assertLayer(layer, maskOf(argb), tif.samples(), PhotoshopLayer.COMPRESSION_RLE);
    }

    static void assertLayer(Layer layer, byte[] mask, byte[] samples, int compression) {
        assertThat(layer.count()).isEqualTo(1);
        assertThat(new int[] {layer.top(), layer.left(), layer.bottom(), layer.right()})
                .containsExactly(0, 0, H, W);
        assertThat(layer.blend()).isEqualTo("norm");
        assertThat(layer.opacity()).isEqualTo(255);
        assertThat(layer.flags()).isEqualTo(0x08);
        assertThat(layer.name()).isEqualTo("Layer 1");
        assertThat(layer.channels()).extracting(LayerChannel::id).containsExactly(-1, 0, 1, 2, 3);
        assertThat(layer.channels()).extracting(LayerChannel::compression).containsOnly(compression);

        byte[] transparency = layer.channels().get(0).decoded();
        for (int i = 0; i < W * H; i++) {
            // transparency uses the UNCONTRACTED mask and is not inverted
            assertThat(transparency[i]).isEqualTo(mask[i] != 0 ? (byte) 0xFF : 0);
            for (int c = 0; c < 4; c++) {
                int composite = samples[i * 5 + c] & 0xFF;
                assertThat(layer.channels().get(c + 1).decoded()[i] & 0xFF).isEqualTo(255 - composite);
            }
        }
    }

    @Test
    void bandHeightDoesNotChangeASingleByte() throws IOException {
        TifEncoder.Options options = options(1, TifCompression.DEFLATE, true, true);
        byte[] reference = Files.readAllBytes(encode("whole.tif", H, options));
        for (int rows : new int[] {1, 7, 64, 65, 100}) {
            byte[] banded = Files.readAllBytes(encode("band" + rows + ".tif", rows, options));
            assertThat(java.util.Arrays.equals(banded, reference)).as("band of %d rows", rows).isTrue();
        }
    }

    @Test
    void lzwStripsDecodeWithTheJdkReaderToTheSameSamples() throws IOException {
        byte[] deflate = read(encode("d.tif", 50, defaults())).samples();
        Path lzw = encode("l.tif", 50, options(0, TifCompression.LZW, false, true));
        Tiff tif = read(lzw);
        assertThat(tif.get(TifEncoder.TAG_COMPRESSION)).isEqualTo(5);
        assertThat(tif.fields()).doesNotContainKey(TifEncoder.TAG_IMAGE_SOURCE_DATA);
        assertThat(jdkRaster(lzw)).isEqualTo(deflate);
        assertThat(jdkRaster(dir.resolve("d.tif"))).isEqualTo(deflate);
    }

    @Test
    void lzwHandlesLongIncompressibleStripsAcrossTableResets() {
        byte[] data = new byte[300_000];
        long seed = 42;
        for (int i = 0; i < data.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            data[i] = (byte) (seed >>> 56);
        }
        var out = new java.io.ByteArrayOutputStream();
        try (StripCodec codec = StripCodec.of(TifCompression.LZW, 0)) {
            codec.compress(data, data.length, out);
            assertThat(LzwTestDecoder.decode(out.toByteArray())).isEqualTo(data);
            out.reset();
            codec.compress(data, 0, out);
            assertThat(LzwTestDecoder.decode(out.toByteArray())).isEmpty();
        }
    }

    /** Reads the raw samples with the JDK TIFF reader: an independent decoder for our strips. */
    static byte[] jdkRaster(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("tiff").next();
            try {
                reader.setInput(in);
                Raster raster = reader.read(0).getRaster();
                assertThat(raster.getNumBands()).isEqualTo(5);
                int[] values = raster.getPixels(0, 0, W, H, (int[]) null);
                byte[] out = new byte[values.length];
                for (int i = 0; i < values.length; i++) {
                    out[i] = (byte) values[i];
                }
                return out;
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void withoutTheLayerTheCompositeIsUnchanged() throws IOException {
        byte[] with = read(encode("with.tif", 64, defaults())).samples();
        Tiff without = read(encode("without.tif", 64, options(0, TifCompression.DEFLATE, false, true)));
        assertThat(without.fields()).doesNotContainKey(TifEncoder.TAG_IMAGE_SOURCE_DATA);
        assertThat(without.samples()).isEqualTo(with);
    }

    @Test
    void singleStripSheetKeepsOffsetsInline() throws IOException {
        Path target = dir.resolve("small.tif");
        byte[] mask = new byte[3 * 2];
        mask[0] = 1;
        try (TifEncoder encoder = new TifEncoder(target, 3, 2, 72, mask, defaults())) {
            encoder.accept(0, 2, 3, new int[] {0xFF000000, 0, 0, 0, 0, 0});
            encoder.finish();
        }
        Tiff tif = read(target);
        assertThat(tif.all(TifEncoder.TAG_STRIP_OFFSETS)).hasSize(1);
        assertThat(tif.rational(TifEncoder.TAG_X_RESOLUTION)).containsExactly(72, 1);
        byte[] samples = TiffTestReader.inflate(tif.bytes(), (int) tif.get(TifEncoder.TAG_STRIP_OFFSETS),
                (int) tif.get(TifEncoder.TAG_STRIP_BYTE_COUNTS));
        assertThat(samples).hasSize(30);
        assertThat(samples[4]).isEqualTo((byte) 0);
        assertThat(samples[9]).isEqualTo((byte) 0xFF);
    }

    @Test
    void rejectsBandsOutOfOrderOrOfTheWrongShape() throws IOException {
        Path target = dir.resolve("bad.tif");
        try (TifEncoder encoder = new TifEncoder(target, W, H, DPI, new byte[W * H], defaults())) {
            assertThatThrownBy(() -> encoder.accept(10, 5, W, new int[5 * W]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> encoder.accept(0, 5, W - 1, new int[5 * W]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> encoder.accept(0, 5, W, new int[4 * W]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> encoder.accept(0, H + 1, W, new int[(H + 1) * W]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> encoder.accept(0, 0, W, new int[0]))
                    .isInstanceOf(IllegalArgumentException.class);
            encoder.accept(0, 5, W, new int[5 * W]);
            assertThatThrownBy(encoder::finish).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("5 of 120");
        }
        assertThat(target).doesNotExist();
        try (Stream<Path> left = Files.list(dir)) {
            assertThat(left).as("partial file and layer temp files are deleted").isEmpty();
        }
    }

    @Test
    void rejectsAMaskOfTheWrongSizeAndBadDimensions() {
        Path target = dir.resolve("bad.tif");
        assertThatThrownBy(() -> new TifEncoder(target, W, H, DPI, new byte[10], defaults()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TifEncoder(target, 0, H, DPI, new byte[0], defaults()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TifEncoder(target, W, H, 0, new byte[W * H], defaults()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(target.resolveSibling("bad.tif.part")).doesNotExist();
    }

    @Test
    void cannotBeUsedAfterFinishOrClose() throws IOException {
        Path target = dir.resolve("once.tif");
        TifEncoder encoder = new TifEncoder(target, 2, 1, DPI, new byte[2], defaults());
        encoder.accept(0, 1, 2, new int[2]);
        encoder.finish();
        assertThatThrownBy(encoder::finish).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> encoder.accept(0, 1, 2, new int[2])).isInstanceOf(IllegalStateException.class);
        encoder.close();
        encoder.close();
        assertThat(target).exists();
    }

    @Test
    void failsCleanlyWhenTheTargetDirectoryDoesNotExist() {
        Path target = dir.resolve("missing").resolve("x.tif");
        assertThatThrownBy(() -> new TifEncoder(target, W, H, DPI, new byte[W * H], defaults()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void packBitsRoundTripsLiteralsRunsAndLongStretches() {
        byte[] data = new byte[1000];
        for (int i = 0; i < data.length; i++) {
            // literals, pairs (kept literal), short and >128-byte runs
            data[i] = (byte) (i < 300 ? i * 31 : i < 310 ? (i / 2) : i < 700 ? 9 : i % 3 == 0 ? 1 : 2);
        }
        var out = new java.io.ByteArrayOutputStream();
        PhotoshopLayer.packBits(data, out);
        byte[] packed = out.toByteArray();
        assertThat(packed.length).isLessThan(data.length);
        assertThat(TiffTestReader.unpackBits(packed, 0, packed.length)).isEqualTo(data);
    }

    @Test
    void deflateLevelFollowsTheJdkMapping() {
        assertThat(StripCodec.deflateLevel(0.3)).isEqualTo(3);
        assertThat(StripCodec.deflateLevel(0)).isEqualTo(1);
        assertThat(StripCodec.deflateLevel(1)).isEqualTo(9);
    }
}
