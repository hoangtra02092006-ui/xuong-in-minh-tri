package vn.minhtri.xuongin.export.tif;

/** Lossless compression of the composite image strips (spec 10.2). */
public enum TifCompression {
    /** Adobe Deflate, TIFF code 8. Default. */
    DEFLATE,
    /** LZW, TIFF code 5. */
    LZW
}
