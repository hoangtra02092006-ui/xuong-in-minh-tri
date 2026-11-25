package vn.minhtri.xuongin.nesting.model;

import java.util.List;

/** Totals (spec 9). byType sorted by categoryIndex; byType lengths sum exactly to totalLengthMm. */
public record NestStats(
        int totalSheets,
        double totalLengthMm,
        double totalShapeAreaMm2,
        double usedAreaMm2,
        double fillRate,
        double savedVsIndividualPct,
        int totalPieces,
        List<TypeStats> byType) {}
