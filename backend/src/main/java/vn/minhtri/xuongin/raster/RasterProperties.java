package vn.minhtri.xuongin.raster;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Raster settings shared by the TIF and cut-file exports (spec 10.3, 10.6, 13).
 *
 * @param maxHeapFraction       share of the max heap one sheet may use (APP_TIFF_MAX_HEAP_FRACTION)
 * @param maxMegapixels         absolute pixel cap of one sheet (APP_TIFF_MAX_MEGAPIXELS)
 * @param bandRows              rows rendered per band, rounded up to a multiple of 64 (APP_TIFF_BAND_ROWS)
 * @param whiteAlphaThreshold   alpha strictly above this counts as ink (APP_TIFF_WHITE_ALPHA_THRESHOLD)
 * @param whiteTolerance        "near white" tolerance against 255 for the background flood (APP_TIFF_WHITE_TOLERANCE)
 */
@ConfigurationProperties(prefix = "app.tiff")
public record RasterProperties(
        Double maxHeapFraction,
        Integer maxMegapixels,
        Integer bandRows,
        Integer whiteAlphaThreshold,
        Integer whiteTolerance) {

    public static final double DEFAULT_MAX_HEAP_FRACTION = 0.5;
    public static final int DEFAULT_MAX_MEGAPIXELS = 100;
    public static final int DEFAULT_BAND_ROWS = 1024;
    public static final int DEFAULT_ALPHA_THRESHOLD = 128;
    public static final int DEFAULT_WHITE_TOLERANCE = 6;

    /** Missing values (no entry in application.yml) fall back to the spec defaults. */
    public RasterProperties {
        maxHeapFraction = maxHeapFraction == null ? DEFAULT_MAX_HEAP_FRACTION : maxHeapFraction;
        maxMegapixels = maxMegapixels == null ? DEFAULT_MAX_MEGAPIXELS : maxMegapixels;
        bandRows = bandRows == null ? DEFAULT_BAND_ROWS : bandRows;
        whiteAlphaThreshold = whiteAlphaThreshold == null ? DEFAULT_ALPHA_THRESHOLD : whiteAlphaThreshold;
        whiteTolerance = whiteTolerance == null ? DEFAULT_WHITE_TOLERANCE : whiteTolerance;
    }

    public static RasterProperties defaults() {
        return new RasterProperties(null, null, null, null, null);
    }
}
