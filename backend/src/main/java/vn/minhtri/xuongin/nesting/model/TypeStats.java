package vn.minhtri.xuongin.nesting.model;

/** Paper length attributed to one design (spec 9). width/height are before rotation. */
public record TypeStats(
        String fileId,
        String label,
        int categoryIndex,
        int pieces,
        double widthMm,
        double heightMm,
        double shapeAreaMm2,
        double lengthMm) {}
