package vn.minhtri.xuongin.nesting;

import java.util.ArrayList;
import java.util.List;
import vn.minhtri.xuongin.engine.EngineException;
import vn.minhtri.xuongin.engine.EngineItem;
import vn.minhtri.xuongin.engine.EngineMode;
import vn.minhtri.xuongin.engine.EnginePlacement;
import vn.minhtri.xuongin.engine.EngineResult;
import vn.minhtri.xuongin.engine.EngineSheet;
import vn.minhtri.xuongin.engine.EngineStats;
import vn.minhtri.xuongin.engine.NestStatsCalculator;
import vn.minhtri.xuongin.engine.EngineParams;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.nesting.model.NestStats;
import vn.minhtri.xuongin.nesting.model.NestingMode;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.nesting.model.TypeStats;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.shared.Units;

/** Converts the engine's output (1/100 mm) into the API model (mm, 2 decimals; ratios 4 decimals). */
public final class EngineResultMapper {

    private static final double RATIO_SCALE = 10_000.0;

    private EngineResultMapper() {}

    public static EngineMode toEngineMode(NestingMode mode) {
        return switch (mode == null ? NestingMode.ORTHOGONAL : mode) {
            case ORTHOGONAL -> EngineMode.ORTHOGONAL;
            case FREE -> EngineMode.FREE;
            case TRUE_SHAPE -> EngineMode.TRUE_SHAPE;
        };
    }

    public static ApiException toApiException(EngineException e) {
        ErrorCode code = switch (e.kind()) {
            case INVALID_REQUEST -> ErrorCode.INVALID_REQUEST;
            case ITEM_WIDER_THAN_SHEET -> ErrorCode.ITEM_WIDER_THAN_SHEET;
            case NESTING_FAILED -> ErrorCode.NESTING_FAILED;
        };
        return new ApiException(code, e.getMessage(), e.details());
    }

    /** Computes the statistics and maps everything; {@code items} must be the list the engine received. */
    public static NestResult toNestResult(List<EngineItem> items, EngineParams params, EngineResult result) {
        return toNestResult(items, result, NestStatsCalculator.compute(items, params, result));
    }

    public static NestResult toNestResult(List<EngineItem> items, EngineResult result, EngineStats stats) {
        List<Sheet> sheets = new ArrayList<>(result.sheets().size());
        for (int i = 0; i < result.sheets().size(); i++) {
            EngineSheet sheet = result.sheets().get(i);
            List<Placement> placements = sheet.placements().stream().map(p -> toPlacement(items, p)).toList();
            sheets.add(new Sheet(i, mm(sheet.width()), mm(sheet.length()), round4(stats.sheetFillRates().get(i)),
                    placements));
        }
        List<TypeStats> byType = stats.byType().stream().map(row -> {
            EngineItem item = items.get(row.itemIndex());
            return new TypeStats(item.id(), item.label(), item.categoryIndex(), row.pieces(), mm(item.w()),
                    mm(item.h()), areaMm2(row.shapeArea()), mm(row.length()));
        }).toList();
        NestStats nestStats = new NestStats(stats.totalSheets(), mm(stats.totalLength()),
                areaMm2(stats.totalShapeArea()), areaMm2(stats.usedArea()), round4(stats.fillRate()),
                Units.round2(stats.savedPct()), stats.totalPieces(), byType);
        return new NestResult(sheets, nestStats);
    }

    private static Placement toPlacement(List<EngineItem> items, EnginePlacement p) {
        EngineItem item = items.get(p.itemIndex());
        return new Placement(item.id(), item.label(), item.categoryIndex(), mm(p.x()), mm(p.y()), mm(p.w()),
                mm(p.h()), p.rotated(), p.angleDeg(), mm(item.w()), mm(item.h()));
    }

    private static double mm(long centi) {
        return Units.round2(Units.centiToMm(centi));
    }

    /** (1/100 mm)^2 to mm^2: one conversion per axis. */
    private static double areaMm2(long centiSquared) {
        return Units.round2(Units.centiToMm(1) * Units.centiToMm(centiSquared));
    }

    private static double round4(double ratio) {
        return Math.round(ratio * RATIO_SCALE) / RATIO_SCALE;
    }
}
