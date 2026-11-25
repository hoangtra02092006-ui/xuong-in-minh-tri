package vn.minhtri.xuongin.engine;

import java.util.List;

/** One exported file: width and length in 1/100 mm, placements sorted by (y, x, pieceId). */
public record EngineSheet(long width, long length, List<EnginePlacement> placements) {

    public EngineSheet {
        placements = List.copyOf(placements);
    }
}
