package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NestingEngineTest {

    private final NestingEngine engine = new NestingEngine();

    /** samples/README scenario D: logo 30x5 cm x 12 on a 57 cm roll, margin 0.5, gap 0.3. */
    private static List<EngineItem> logos(boolean allowRotate) {
        return List.of(new EngineItem("logo", "logo-30x5.pdf", 0, 30_000, 5_000, 12, allowRotate));
    }

    @Test
    void rowRotateFlagOffKeepsOrientationAndGivesTheExpectedLength() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        EngineResult result = InvariantChecker.nestAndCheck(logos(false), params);
        // One per row: 12 x 5.3 cm - the last gap + 2 margins = 64.3 cm.
        assertThat(result.totalLength()).isEqualTo(64_300);
        assertThat(result.sheets().get(0).placements()).noneMatch(EnginePlacement::rotated);
    }

    @Test
    void rotationOnIsMuchShorter() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        EngineResult result = InvariantChecker.nestAndCheck(logos(true), params);
        assertThat(result.totalLength()).isLessThanOrEqualTo(41_600);
        assertThat(result.sheets().get(0).placements()).anyMatch(EnginePlacement::rotated);
    }

    @Test
    void globalSwitchOffOverridesEveryRow() {
        for (EngineMode mode : EngineMode.values()) {
            EngineParams params = new EngineParams(mode, 57_000, 500, 300, null, false);
            EngineResult result = InvariantChecker.nestAndCheck(logos(true), params);
            assertThat(result.sheets().get(0).placements()).allMatch(p -> p.totalAngleDeg() == 0);
        }
    }

    @Test
    void marginTooLargeIsInvalid() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 1_000, 500, 300, null, true);
        assertThatThrownBy(() -> engine.nest(logos(true), params))
                .isInstanceOfSatisfying(EngineException.class, e -> {
                    assertThat(e.kind()).isEqualTo(EngineException.Kind.INVALID_REQUEST);
                    assertThat(e.getMessage()).startsWith("Lề biên quá lớn");
                });
    }

    @Test
    void emptyListIsInvalid() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        assertThatThrownBy(() -> engine.nest(List.of(), params))
                .isInstanceOfSatisfying(EngineException.class,
                        e -> assertThat(e.kind()).isEqualTo(EngineException.Kind.INVALID_REQUEST));
    }

    @Test
    void itemWiderThanSheetEvenRotatedNamesTheFile() {
        List<EngineItem> items = List.of(new EngineItem("x", "loi-qua-kho-60x59.pdf", 0, 60_000, 59_000, 1, true));
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        assertThatThrownBy(() -> engine.nest(items, params))
                .isInstanceOfSatisfying(EngineException.class, e -> {
                    assertThat(e.kind()).isEqualTo(EngineException.Kind.ITEM_WIDER_THAN_SHEET);
                    assertThat(e.getMessage()).isEqualTo(
                            "Hình \"loi-qua-kho-60x59.pdf\" 60x59 cm rộng hơn khổ 57 cm (đã tính cả trường hợp xoay).");
                    assertThat(e.details()).containsEntry("fileId", "x").containsEntry("label", "loi-qua-kho-60x59.pdf");
                });
    }

    @Test
    void itemWiderThanSheetWithoutRotationSaysSo() {
        List<EngineItem> items = List.of(new EngineItem("b", "bang-ron.pdf", 0, 60_000, 20_000, 1, false));
        EngineParams params = new EngineParams(EngineMode.FREE, 57_000, 500, 300, null, true);
        assertThatThrownBy(() -> engine.nest(items, params))
                .hasMessage("Hình \"bang-ron.pdf\" 60x20 cm rộng hơn khổ 57 cm và không được phép xoay.");
    }

    @Test
    void wideItemFitsWhenRotated() {
        List<EngineItem> items = List.of(new EngineItem("b", "b.pdf", 0, 60_000, 20_000, 2, true));
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        EngineResult result = InvariantChecker.nestAndCheck(items, params);
        assertThat(result.sheets().get(0).placements()).allMatch(EnginePlacement::rotated);
    }

    @Test
    void maxLengthBelowTallestPieceIsInvalid() {
        List<EngineItem> items = List.of(new EngineItem("t", "t.pdf", 0, 10_000, 30_000, 1, false));
        for (EngineMode mode : EngineMode.values()) {
            EngineParams params = new EngineParams(mode, 57_000, 500, 300, 20_000L, true);
            assertThatThrownBy(() -> engine.nest(items, params))
                    .isInstanceOfSatisfying(EngineException.class, e -> {
                        assertThat(e.kind()).isEqualTo(EngineException.Kind.INVALID_REQUEST);
                        assertThat(e.getMessage()).isEqualTo("Chiều dài tối đa mỗi file (20 cm) nhỏ hơn hình cao "
                                + "nhất (31 cm, kể cả lề). Hãy tăng giới hạn chiều dài.");
                    });
        }
    }

    @Test
    void sheetIsNeverLongerThanTheLimitEvenWithALargeGap() {
        // Old bug: limit 200 cm, gap 5 cm -> 205 cm sheets.
        List<EngineItem> items = List.of(new EngineItem("a", "a.pdf", 0, 15_000, 9_500, 9, true));
        for (EngineMode mode : EngineMode.values()) {
            EngineParams params = new EngineParams(mode, 57_000, 0, 5_000, 20_000L, false);
            EngineResult result = InvariantChecker.nestAndCheck(items, params);
            assertThat(result.sheets()).allMatch(s -> s.length() <= 20_000);
        }
    }

    @Test
    void tooManySheetsIsInvalid() {
        List<EngineItem> items = List.of(new EngineItem("a", "a.pdf", 0, 1_000, 1_000, 201, false));
        EngineParams params = new EngineParams(EngineMode.TRUE_SHAPE, 1_000, 0, 0, 1_000L, false);
        assertThatThrownBy(() -> engine.nest(items, params))
                .isInstanceOfSatisfying(EngineException.class, e -> {
                    assertThat(e.kind()).isEqualTo(EngineException.Kind.INVALID_REQUEST);
                    assertThat(e.getMessage()).startsWith("Số tấm vượt quá giới hạn an toàn (200 tấm)");
                });
    }

    @Test
    void trueShapePieceTooTallForItsGridIsInvalid() {
        // 10.05 cm needs 101 one-millimetre rows; the limit leaves 100.
        List<EngineItem> items = List.of(new EngineItem("a", "a.pdf", 0, 5_000, 10_050, 1, false));
        EngineParams params = new EngineParams(EngineMode.TRUE_SHAPE, 57_000, 0, 0, 10_070L, false);
        assertThatThrownBy(() -> engine.nest(items, params)).isInstanceOf(EngineException.class)
                .hasMessageContaining("Chiều dài tối đa mỗi file");
    }

    @Test
    void trueShapePieceWiderThanItsGridIsReported() {
        // Fits the usable width by 0.05 mm but needs one more 1 mm column than the grid has.
        List<EngineItem> items = List.of(new EngineItem("a", "a.pdf", 0, 10_050, 1_000, 1, false));
        EngineParams params = new EngineParams(EngineMode.TRUE_SHAPE, 10_070, 0, 0, null, false);
        assertThatThrownBy(() -> engine.nest(items, params))
                .isInstanceOfSatisfying(EngineException.class,
                        e -> assertThat(e.kind()).isEqualTo(EngineException.Kind.ITEM_WIDER_THAN_SHEET));
    }

    @Test
    void progressIsMonotonicAndEndsAtTotal() {
        for (EngineMode mode : EngineMode.values()) {
            for (Long max : new Long[] {null, 15_000L}) {
                List<long[]> calls = new ArrayList<>();
                EngineParams params = new EngineParams(mode, 57_000, 500, 300, max, true);
                engine.nest(logos(true), params, (tried, total) -> calls.add(new long[] {tried, total}));
                assertThat(calls).isNotEmpty();
                long[] last = calls.get(calls.size() - 1);
                assertThat(last[0]).isEqualTo(last[1]);
                for (int i = 1; i < calls.size(); i++) {
                    assertThat(calls.get(i)[0]).isGreaterThanOrEqualTo(calls.get(i - 1)[0]);
                    assertThat(calls.get(i)[0]).isLessThanOrEqualTo(calls.get(i)[1]);
                }
            }
        }
    }

    @Test
    void placementsAreSortedAndIdsAreStable() {
        EngineParams params = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);
        EngineResult result = engine.nest(AcceptanceTest.ORDER.subList(0, 2), params);
        List<EnginePlacement> ps = result.sheets().get(0).placements();
        for (int i = 1; i < ps.size(); i++) {
            EnginePlacement a = ps.get(i - 1);
            EnginePlacement b = ps.get(i);
            assertThat(a.y() < b.y() || (a.y() == b.y() && a.x() <= b.x())).isTrue();
        }
        assertThat(ps.stream().mapToInt(EnginePlacement::pieceId).sorted().toArray())
                .containsExactly(java.util.stream.IntStream.range(0, 40).toArray());
    }

    @Test
    void inputValidationOfRecords() {
        assertThatThrownBy(() -> new EngineItem("a", "a", 0, 0, 10, 1, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngineItem("a", "a", 0, 10, 10, 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngineParams(EngineMode.FREE, 0, 0, 0, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EngineParams(EngineMode.FREE, 10, 0, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cmFormatterUsesVietnameseDecimalComma() {
        assertThat(NestingEngine.cm(500)).isEqualTo("0,5");
        assertThat(NestingEngine.cm(57_000)).isEqualTo("57");
        assertThat(NestingEngine.cm(33_870)).isEqualTo("33,87");
    }
}
