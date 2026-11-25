package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Spec 15: fixed-seed random sweep, 24 configurations x 3 modes, many margins / gaps / length limits.
 * The only kind of test that ever caught a sheet longer than its limit.
 */
class RandomSweepTest {

    private static final int CONFIGS = 24;
    private static final long SEED = 20240917L;

    record Config(int index, List<EngineItem> items, long width, long margin, long gap, Long maxLength,
                  boolean rotate) {

        @Override
        public String toString() {
            return "config " + index + " (" + items.size() + " designs, width " + width + ", margin " + margin
                    + ", gap " + gap + ", max " + maxLength + ", rotate " + rotate + ")";
        }
    }

    static Stream<Arguments> cases() {
        List<Config> configs = configs();
        return Stream.of(EngineMode.values())
                .flatMap(mode -> configs.stream().map(c -> Arguments.of(mode, c)));
    }

    static List<Config> configs() {
        Random random = new Random(SEED);
        List<Config> configs = new ArrayList<>();
        for (int i = 0; i < CONFIGS; i++) {
            long width = 20_000 + random.nextInt(40_001);
            long margin = random.nextInt(5) * 250L;
            long gap = random.nextInt(7) * 100L + (random.nextBoolean() ? 50 : 0);
            boolean rotate = random.nextInt(4) != 0;
            int designs = 1 + random.nextInt(4);
            List<EngineItem> items = new ArrayList<>();
            long longest = 0;
            for (int d = 0; d < designs; d++) {
                long usable = width - 2 * margin;
                long w = Math.min(usable, 1_000 + random.nextInt(15_000));
                long h = 1_000 + random.nextInt(15_000);
                boolean allow = random.nextInt(3) != 0;
                int quantity = 1 + random.nextInt(8);
                boolean shaped = random.nextBoolean();
                items.add(shaped
                        ? new EngineItem("f" + d, "f" + d + ".pdf", d, w, h, quantity, allow, Masks.lShape2(w, h))
                        : new EngineItem("f" + d, "f" + d + ".pdf", d, w, h, quantity, allow));
                longest = Math.max(longest, h);
            }
            Long maxLength = random.nextBoolean()
                    ? null
                    : Math.max(longest + 2 * margin + 200, 10_000 + random.nextInt(30_000));
            configs.add(new Config(i, items, width, margin, gap, maxLength, rotate));
        }
        return configs;
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void everyInvariantHolds(EngineMode mode, Config config) {
        EngineParams params = new EngineParams(mode, config.width(), config.margin(), config.gap(),
                config.maxLength(), config.rotate());
        EngineResult result = InvariantChecker.nestAndCheck(config.items(), params);

        EngineStats stats = NestStatsCalculator.compute(config.items(), params, result);
        assertThat(stats.fillRate()).isBetween(0.0, 1.0);
        stats.sheetFillRates().forEach(f -> assertThat(f).isBetween(0.0, 1.0));
        assertThat(stats.byType().stream().mapToLong(EngineStats.TypeLength::length).sum())
                .isEqualTo(stats.totalLength());
        assertThat(stats.byType()).allSatisfy(row -> assertThat(row.length()).isPositive());
    }

    @Test
    void sweepHasEnoughConfigurations() {
        List<Config> configs = configs();
        assertThat(configs).hasSizeGreaterThanOrEqualTo(22);
        assertThat(configs).anyMatch(c -> c.maxLength() != null).anyMatch(c -> c.maxLength() == null);
    }
}
