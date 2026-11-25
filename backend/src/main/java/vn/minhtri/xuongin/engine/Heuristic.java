package vn.minhtri.xuongin.engine;

/** MaxRects scoring rules (spec 8.2). Lower score wins; no rule wins on every data set, so all are tried. */
enum Heuristic {
    /** Best short side fit. */
    BSSF,
    /** Best long side fit. */
    BLSF,
    /** Best area fit. */
    BAF,
    /** Bottom-left: lowest top, then leftmost. */
    BL
}
