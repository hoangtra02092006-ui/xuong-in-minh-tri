package vn.minhtri.xuongin.export.tif;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Manual measurement on a full 570 × 1000 mm sheet at 300 DPI (6732 × 11811 px) with the real
 * ICC profile. Run with {@code ./mvnw test -Dtest=TifEncoderBenchmarkTest -Dtif.benchmark=true}.
 * The artwork is synthetic: flat-colour discs on a transparent background covering about half the
 * sheet, fed in bands of 1024 rows like the rasterizer does.
 */
@EnabledIfSystemProperty(named = "tif.benchmark", matches = "true")
class TifEncoderBenchmarkTest {

    private static final int WIDTH = 6732;
    private static final int HEIGHT = 11811;
    private static final int BAND = 1024;
    private static final int TILE = 700;
    private static final int RADIUS = 300;

    @TempDir
    Path dir;

    @Test
    void fullSheet() throws IOException {
        Path profile = TifEncoderFactoryTest.swopProfile();
        TifEncoderFactory factory = new TifEncoderFactory(
                new TifProperties(profile.toString(), TifCompression.DEFLATE, 0.3, true, true, "W1", 1));
        byte[] mask = new byte[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                mask[y * WIDTH + x] = (byte) ((pixel(x, y) >>> 24) > 128 ? 1 : 0);
            }
        }
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long before = rt.totalMemory() - rt.freeMemory();
        long peak = before;
        long start = System.nanoTime();
        Path target = dir.resolve("sheet.tif");
        TifResult result;
        try (TifEncoder encoder = factory.open(target, WIDTH, HEIGHT, 300, mask)) {
            int[] band = new int[BAND * WIDTH];
            for (int y = 0; y < HEIGHT; y += BAND) {
                int rows = Math.min(BAND, HEIGHT - y);
                for (int r = 0; r < rows; r++) {
                    for (int x = 0; x < WIDTH; x++) {
                        band[r * WIDTH + x] = pixel(x, y + r);
                    }
                }
                encoder.accept(y, rows, WIDTH, band);
                peak = Math.max(peak, rt.totalMemory() - rt.freeMemory());
            }
            result = encoder.finish();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("[TIF BENCH] %d x %d px, %.1f MB, %d ms, heap above mask+band ~%d MB (sampled)%n",
                WIDTH, HEIGHT, result.sizeBytes() / 1e6, ms, (peak - before) / (1 << 20));
        Files.delete(target);
    }

    /** Flat-colour discs with an outline (logo-like) on a transparent background, a soft anti-aliased edge. */
    private static int pixel(int x, int y) {
        int dx = x % TILE - TILE / 2;
        int dy = y % TILE - TILE / 2;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > RADIUS + 1) {
            return 0;
        }
        int alpha = d < RADIUS ? 255 : 128;
        int tile = (y / TILE) * 31 + x / TILE;
        int colour = (tile * 0x2F6B1D) & 0xFFFFFF;
        // a darker ring like a logo outline
        return alpha << 24 | (d > RADIUS - 20 ? colour >> 1 & 0x7F7F7F : colour);
    }
}
