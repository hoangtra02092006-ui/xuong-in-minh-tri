package vn.minhtri.xuongin.engine;

/** Packing mode of the engine (mirrors the API's NestingMode; kept separate so the engine has no web dependency). */
public enum EngineMode {
    /** Bounding rectangles, 0 or 90 degrees. */
    ORTHOGONAL,
    /** Like ORTHOGONAL, plus small pieces may sit inside empty cavities of a bigger piece's box. */
    FREE,
    /** Pieces interlock by their real outline on a 1 mm bit grid, 8 angles. */
    TRUE_SHAPE
}
