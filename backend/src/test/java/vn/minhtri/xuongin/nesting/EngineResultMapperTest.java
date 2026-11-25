package vn.minhtri.xuongin.nesting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.engine.EngineException;
import vn.minhtri.xuongin.engine.EngineItem;
import vn.minhtri.xuongin.engine.EngineMode;
import vn.minhtri.xuongin.engine.EngineParams;
import vn.minhtri.xuongin.engine.EnginePlacement;
import vn.minhtri.xuongin.engine.EngineResult;
import vn.minhtri.xuongin.engine.EngineSheet;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.nesting.model.NestingMode;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.TypeStats;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

class EngineResultMapperTest {

    private static final List<EngineItem> ITEMS = List.of(
            new EngineItem("f-a", "nhan-20x18.pdf", 0, 20_000, 18_000, 1, true),
            new EngineItem("f-b", "nhan-7x3.pdf", 1, 7_000, 3_000, 1, true));
    private static final EngineParams PARAMS = new EngineParams(EngineMode.ORTHOGONAL, 57_000, 500, 300, null, true);

    @Test
    void convertsToMillimetresWithStatsThatAddUp() {
        EngineResult result = new EngineResult(List.of(new EngineSheet(57_000, 19_000, List.of(
                new EnginePlacement(0, 0, 500, 500, 20_000, 18_000, false, 0),
                new EnginePlacement(1, 1, 20_800, 500, 3_000, 7_000, true, 0)))));

        NestResult nest = EngineResultMapper.toNestResult(ITEMS, PARAMS, result);

        assertThat(nest.sheets()).hasSize(1);
        assertThat(nest.sheets().get(0).widthMm()).isEqualTo(570.0);
        assertThat(nest.sheets().get(0).lengthMm()).isEqualTo(190.0);
        Placement rotated = nest.sheets().get(0).placements().get(1);
        assertThat(rotated).isEqualTo(new Placement("f-b", "nhan-7x3.pdf", 1, 208.0, 5.0, 30.0, 70.0, true, 0,
                70.0, 30.0));
        // (200 x 180 + 30 x 70) / (570 x 190) = 38100 / 108300
        assertThat(nest.sheets().get(0).fillRate()).isEqualTo(0.3518);
        assertThat(nest.stats().fillRate()).isEqualTo(0.3518);
        assertThat(nest.stats().totalLengthMm()).isEqualTo(190.0);
        assertThat(nest.stats().totalShapeAreaMm2()).isEqualTo(38_100.0);
        assertThat(nest.stats().usedAreaMm2()).isEqualTo(108_300.0);
        assertThat(nest.stats().totalPieces()).isEqualTo(2);
        assertThat(nest.stats().totalSheets()).isEqualTo(1);
        // One by one: 10 + (180 + 3) + (30 + 3) = 226 mm -> 1 - 190/226 = 15.93 %
        assertThat(nest.stats().savedVsIndividualPct()).isEqualTo(15.93);
        List<TypeStats> byType = nest.stats().byType();
        assertThat(byType).extracting(TypeStats::fileId).containsExactly("f-a", "f-b");
        assertThat(byType.get(0).widthMm()).isEqualTo(200.0);
        assertThat(byType.get(0).shapeAreaMm2()).isEqualTo(36_000.0);
        // 19000 x 36000/38100 = 17952.75... -> 17953 ; 19000 x 2100/38100 = 1047.24 -> 1047
        assertThat(byType).extracting(TypeStats::lengthMm).containsExactly(179.53, 10.47);
    }

    @Test
    void mapsModesAndErrors() {
        assertThat(EngineResultMapper.toEngineMode(null)).isEqualTo(EngineMode.ORTHOGONAL);
        assertThat(EngineResultMapper.toEngineMode(NestingMode.FREE)).isEqualTo(EngineMode.FREE);
        assertThat(EngineResultMapper.toEngineMode(NestingMode.TRUE_SHAPE)).isEqualTo(EngineMode.TRUE_SHAPE);
        assertThat(EngineResultMapper.toEngineMode(NestingMode.ORTHOGONAL)).isEqualTo(EngineMode.ORTHOGONAL);

        ApiException wide = EngineResultMapper.toApiException(new EngineException(
                EngineException.Kind.ITEM_WIDER_THAN_SHEET, "rộng", Map.of("fileId", "x")));
        assertThat(wide.code()).isEqualTo(ErrorCode.ITEM_WIDER_THAN_SHEET);
        assertThat(wide.getMessage()).isEqualTo("rộng");
        assertThat(wide.details()).containsEntry("fileId", "x");
        assertThat(EngineResultMapper.toApiException(
                new EngineException(EngineException.Kind.INVALID_REQUEST, "m")).code()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(EngineResultMapper.toApiException(
                new EngineException(EngineException.Kind.NESTING_FAILED, "m")).code()).isEqualTo(ErrorCode.NESTING_FAILED);
    }
}
