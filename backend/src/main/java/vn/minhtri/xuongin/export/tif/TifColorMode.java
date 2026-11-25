package vn.minhtri.xuongin.export.tif;

/** Colour model of the TIF (spec 10.2, APP_TIFF_COLOR_MODE). */
public enum TifColorMode {
    /** CMYK through the ICC profile, plus the W1 white-ink channel when enabled. Default. */
    CMYK,
    /** RGB as rendered, sRGB profile, never a W1 channel. */
    RGB
}
