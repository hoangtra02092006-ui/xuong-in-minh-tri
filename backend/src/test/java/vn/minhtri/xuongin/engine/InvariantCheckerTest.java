package vn.minhtri.xuongin.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The checker must catch each kind of broken layout, or the other tests prove nothing. */
class InvariantCheckerTest {

    private static final List<EngineItem> TWO = List.of(new EngineItem("a", "a", 0, 1_000, 500, 2, false));
    private static final EngineParams ORTHO = new EngineParams(EngineMode.ORTHOGONAL, 3_000, 100, 200, 2_000L, true);

    private static EngineResult sheet(long length, EnginePlacement... ps) {
        return new EngineResult(List.of(new EngineSheet(3_000, length, List.of(ps))));
    }

    @Test
    void acceptsAValidLayout() {
        EngineResult ok = sheet(700, new EnginePlacement(0, 0, 100, 100, 1_000, 500, false, 0),
                new EnginePlacement(0, 1, 1_300, 100, 1_000, 500, false, 0));
        assertThatCode(() -> InvariantChecker.check(TWO, ORTHO, ok)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPiecesCloserThanTheGap() {
        EngineResult bad = sheet(700, new EnginePlacement(0, 0, 100, 100, 1_000, 500, false, 0),
                new EnginePlacement(0, 1, 1_250, 100, 1_000, 500, false, 0));
        assertThatThrownBy(() -> InvariantChecker.check(TWO, ORTHO, bad)).hasMessageContaining("invariant 3");
    }

    @Test
    void rejectsAMissingPiece() {
        EngineResult bad = sheet(700, new EnginePlacement(0, 0, 100, 100, 1_000, 500, false, 0));
        assertThatThrownBy(() -> InvariantChecker.check(TWO, ORTHO, bad)).hasMessageContaining("invariant 2");
    }

    @Test
    void rejectsAPieceInTheMargin() {
        EngineResult bad = sheet(700, new EnginePlacement(0, 0, 50, 100, 1_000, 500, false, 0),
                new EnginePlacement(0, 1, 1_300, 100, 1_000, 500, false, 0));
        assertThatThrownBy(() -> InvariantChecker.check(TWO, ORTHO, bad)).hasMessageContaining("invariant 3");
    }

    @Test
    void rejectsASheetOverTheLimit() {
        EngineResult bad = sheet(2_100, new EnginePlacement(0, 0, 100, 100, 1_000, 500, false, 0),
                new EnginePlacement(0, 1, 1_300, 100, 1_000, 500, false, 0));
        assertThatThrownBy(() -> InvariantChecker.check(TWO, ORTHO, bad)).hasMessageContaining("invariant 4");
    }

    @Test
    void rejectsARotatedLockedPiece() {
        EngineResult bad = sheet(1_300, new EnginePlacement(0, 0, 100, 100, 500, 1_000, true, 0),
                new EnginePlacement(0, 1, 1_300, 100, 1_000, 500, false, 0));
        assertThatThrownBy(() -> InvariantChecker.check(TWO, ORTHO, bad)).hasMessageContaining("invariant 8");
    }

    @Test
    void rejectsAScaledPiece() {
        List<EngineItem> rotatable = List.of(new EngineItem("a", "a", 0, 1_000, 500, 1, true));
        EngineResult bad = sheet(700, new EnginePlacement(0, 0, 100, 100, 900, 500, false, 0));
        assertThatThrownBy(() -> InvariantChecker.check(rotatable, ORTHO, bad)).hasMessageContaining("invariant 1");
    }

    @Test
    void rejectsTouchingInkEvenWhenBoxesMayOverlap() {
        // Two L shapes whose boxes overlap; the second's arm lies on the first's arm.
        List<EngineItem> ls = List.of(new EngineItem("L", "L", 0, 2_000, 2_000, 2, false, Masks.lShape(2_000, 500)));
        EngineParams free = new EngineParams(EngineMode.FREE, 6_000, 0, 100, null, false);
        EngineResult bad = new EngineResult(List.of(new EngineSheet(6_000, 2_500, List.of(
                new EnginePlacement(0, 0, 0, 0, 2_000, 2_000, false, 0),
                new EnginePlacement(0, 1, 300, 300, 2_000, 2_000, false, 0)))));
        assertThatThrownBy(() -> InvariantChecker.check(ls, free, bad)).hasMessageContaining("ink");

        EngineResult nested = new EngineResult(List.of(new EngineSheet(6_000, 2_500, List.of(
                new EnginePlacement(0, 0, 0, 0, 2_000, 2_000, false, 0),
                new EnginePlacement(0, 1, 1_900, 0, 2_000, 2_000, false, 0)))));
        assertThatThrownBy(() -> InvariantChecker.check(ls, free, nested)).hasMessageContaining("ink");
    }
}
