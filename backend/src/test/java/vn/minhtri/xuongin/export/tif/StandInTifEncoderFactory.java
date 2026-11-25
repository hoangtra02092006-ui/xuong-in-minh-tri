package vn.minhtri.xuongin.export.tif;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Encoder factory for tests without the Adobe ICC profile (CI, ADR-004): same encoder, the
 * deterministic stand-in colour converter of {@link TifEncoderTest}. Everything but the colour
 * values goes through the real code.
 */
public class StandInTifEncoderFactory extends TifEncoderFactory {

    public StandInTifEncoderFactory() {
        super(new TifProperties("stand-in", TifCompression.DEFLATE, 0.3, true, true, "W1", 1));
    }

    @Override
    public TifEncoder open(Path target, int width, int height, int dpi, byte[] inkMask) throws IOException {
        TifEncoder.Options options = new TifEncoder.Options(TifEncoderTest.NAIVE, TifEncoderTest.FAKE_ICC, "W1", 1,
                TifCompression.DEFLATE, 0.3, true, true);
        return new TifEncoder(target, width, height, dpi, inkMask, options);
    }

    /** SamplesPerPixel tag of a TIF written by the encoder (5 = CMYK + W1), for tests outside this package. */
    public static long samplesPerPixel(byte[] tif) {
        return TiffTestReader.parse(tif).get(TifEncoder.TAG_SAMPLES_PER_PIXEL);
    }

    /** ImageLength tag (rows) of a TIF written by the encoder. */
    public static long height(byte[] tif) {
        return TiffTestReader.parse(tif).get(TifEncoder.TAG_IMAGE_LENGTH);
    }
}
