package vn.minhtri.xuongin.nesting.model;

public enum NestingMode {
    /** Bounding rectangles, 0/90 degrees. Default. */
    ORTHOGONAL,
    /** Small pieces may sit fully inside empty cavities of bigger pieces' boxes. */
    FREE,
    /** Pieces interlock by real outline, 8 angles incl. 45 degrees. */
    TRUE_SHAPE
}
