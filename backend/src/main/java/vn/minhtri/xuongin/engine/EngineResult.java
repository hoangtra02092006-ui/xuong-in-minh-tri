package vn.minhtri.xuongin.engine;

import java.util.List;

/** Output of {@link NestingEngine#nest}. The piece count always equals the requested quantity. */
public record EngineResult(List<EngineSheet> sheets) {

    public EngineResult {
        sheets = List.copyOf(sheets);
    }

    public int totalPieces() {
        return sheets.stream().mapToInt(s -> s.placements().size()).sum();
    }

    public long totalLength() {
        return sheets.stream().mapToLong(EngineSheet::length).sum();
    }
}
