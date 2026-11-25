package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Spec 8.6: the workshop's real order. 57 cm roll, gap 0.3 cm, margin 0.5 cm, rotation on, ORTHOGONAL.
 * The new engine must match or beat the old one; thresholds are the old measured numbers.
 */
class AcceptanceTest {

    static final List<EngineItem> ORDER = List.of(
            new EngineItem("f0", "nhan-7x3.pdf", 0, 7000, 3000, 30, true),
            new EngineItem("f1", "nhan-5x7.pdf", 1, 5000, 7000, 10, true),
            new EngineItem("f2", "nhan-13x4.pdf", 2, 13000, 4000, 20, true),
            new EngineItem("f3", "nhan-4x17.pdf", 3, 4000, 17000, 15, true),
            new EngineItem("f4", "nhan-16x13.pdf", 4, 16000, 13000, 20, true),
            new EngineItem("f5", "nhan-20x18.pdf", 5, 20000, 18000, 50, true));

    @Test
    void unlimitedLengthFitsOnOneSheetAtLeastAsShortAsTheOldEngine() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57000, 500, 300, null, true);
        long start = System.nanoTime();
        EngineResult result = InvariantChecker.nestAndCheck(ORDER, params);
        long millis = (System.nanoTime() - start) / 2_000_000;
        EngineStats stats = NestStatsCalculator.compute(ORDER, params, result);
        log("khong gioi han", stats, millis);

        assertThat(stats.totalShapeArea()).isEqualTo(25_200L * 100 * 100 * 100);
        assertThat(stats.totalSheets()).isEqualTo(1);
        assertThat(stats.totalLength()).isLessThanOrEqualTo(478_200);
        assertThat(Math.round(stats.fillRate() * 10_000) / 10_000.0).isGreaterThanOrEqualTo(0.9245);
        assertThat(Math.round(stats.savedPct() * 10) / 10.0).isGreaterThanOrEqualTo(71.9);
        assertThat(stats.byType().stream().mapToLong(EngineStats.TypeLength::length).sum())
                .isEqualTo(stats.totalLength());
    }

    @Test
    void maxLength120cmGivesFiveSheetsNoLongerThanTheOldEngine() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57000, 500, 300, 120_000L, true);
        long start = System.nanoTime();
        EngineResult result = InvariantChecker.nestAndCheck(ORDER, params);
        long millis = (System.nanoTime() - start) / 2_000_000;
        EngineStats stats = NestStatsCalculator.compute(ORDER, params, result);
        log("gioi han 120 cm", stats, millis);

        assertThat(stats.totalSheets()).isEqualTo(5);
        assertThat(stats.totalLength()).isLessThanOrEqualTo(494_100);
        assertThat(stats.totalPieces()).isEqualTo(145);
    }

    private static void log(String scenario, EngineStats s, long millis) {
        System.out.println(String.format(Locale.ROOT,
                "[NGHIEM THU] %s: chieu dai = %.1f cm | lap day = %.2f %% | tiet kiem = %.1f %% | so tam = %d | %d ms",
                scenario, s.totalLength() / 1000.0, s.fillRate() * 100, s.savedPct(), s.totalSheets(), millis));
    }
}
