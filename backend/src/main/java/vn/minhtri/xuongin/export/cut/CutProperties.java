package vn.minhtri.xuongin.export.cut;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cut-file settings (spec 10.5, 13). Every registration-mark value lives here, not in code:
 * the marks have no real sample yet (design section 7). Environment names follow spec 13,
 * e.g. {@code APP_CUT_MARK_TOP_LEFT} binds {@code markTopLeft}.
 *
 * @param dpi              raster DPI for contour tracing (APP_CUT_DPI)
 * @param offsetMm         grow the cut line outwards, on the mask (APP_CUT_OFFSET_MM); 0 = on the W1 edge
 * @param simplifyMm       Douglas–Peucker tolerance (APP_CUT_SIMPLIFY_MM)
 * @param minAreaMm2       drop islands / holes smaller than this; dust only (APP_CUT_MIN_AREA_MM2)
 * @param strokePt         cut line width (APP_CUT_STROKE_PT)
 * @param spotName         Separation colour name the cutter looks for (APP_CUT_SPOT_NAME)
 * @param markLengthMm     registration mark edge (APP_CUT_MARK_LENGTH_MM)
 * @param markThicknessMm  registration mark bar thickness (APP_CUT_MARK_THICKNESS_MM)
 * @param markClearanceMm  empty band kept around each mark (APP_CUT_MARK_CLEARANCE_MM)
 * @param markTopLeft      shape of each corner mark (APP_CUT_MARK_TOP_LEFT, …)
 * @param artworkLayer     also embed the print artwork in a hidden, non-printing "Artwork" layer
 *                         for visual checks (APP_CUT_ARTWORK_LAYER); off by default because the
 *                         spec's cut file holds only the two layers CutContour and RegMarks
 */
@ConfigurationProperties(prefix = "app.cut")
public record CutProperties(
        Integer dpi,
        Double offsetMm,
        Double simplifyMm,
        Double minAreaMm2,
        Double strokePt,
        String spotName,
        Double markLengthMm,
        Double markThicknessMm,
        Double markClearanceMm,
        MarkShape markTopLeft,
        MarkShape markTopRight,
        MarkShape markBottomLeft,
        MarkShape markBottomRight,
        Boolean artworkLayer) {

    public static final int DEFAULT_DPI = 300;
    public static final double DEFAULT_OFFSET_MM = 0.0;
    public static final double DEFAULT_SIMPLIFY_MM = 0.1;
    public static final double DEFAULT_MIN_AREA_MM2 = 0.2;
    public static final double DEFAULT_STROKE_PT = 0.25;
    public static final String DEFAULT_SPOT_NAME = "CutContour";
    public static final double DEFAULT_MARK_LENGTH_MM = 15.0;
    public static final double DEFAULT_MARK_THICKNESS_MM = 1.0;
    public static final double DEFAULT_MARK_CLEARANCE_MM = 5.0;
    public static final MarkShape DEFAULT_MARK_SHAPE = MarkShape.SQUARE_WITH_EDGE;

    /** Missing values (no entry in application.yml, no env var) fall back to the spec defaults. */
    public CutProperties {
        dpi = dpi == null ? DEFAULT_DPI : dpi;
        offsetMm = offsetMm == null ? DEFAULT_OFFSET_MM : offsetMm;
        simplifyMm = simplifyMm == null ? DEFAULT_SIMPLIFY_MM : simplifyMm;
        minAreaMm2 = minAreaMm2 == null ? DEFAULT_MIN_AREA_MM2 : minAreaMm2;
        strokePt = strokePt == null ? DEFAULT_STROKE_PT : strokePt;
        spotName = spotName == null || spotName.isBlank() ? DEFAULT_SPOT_NAME : spotName;
        markLengthMm = markLengthMm == null ? DEFAULT_MARK_LENGTH_MM : markLengthMm;
        markThicknessMm = markThicknessMm == null ? DEFAULT_MARK_THICKNESS_MM : markThicknessMm;
        markClearanceMm = markClearanceMm == null ? DEFAULT_MARK_CLEARANCE_MM : markClearanceMm;
        markTopLeft = markTopLeft == null ? DEFAULT_MARK_SHAPE : markTopLeft;
        markTopRight = markTopRight == null ? DEFAULT_MARK_SHAPE : markTopRight;
        markBottomLeft = markBottomLeft == null ? DEFAULT_MARK_SHAPE : markBottomLeft;
        markBottomRight = markBottomRight == null ? DEFAULT_MARK_SHAPE : markBottomRight;
        artworkLayer = artworkLayer != null && artworkLayer;
        if (dpi <= 0) {
            throw new IllegalArgumentException("app.cut.dpi must be positive: " + dpi);
        }
        if (offsetMm < 0) {
            throw new IllegalArgumentException("app.cut.offset-mm must not be negative: " + offsetMm);
        }
        if (markThicknessMm <= 0 || markThicknessMm > markLengthMm) {
            throw new IllegalArgumentException(
                    "app.cut.mark-thickness-mm must be in (0, mark-length-mm]: " + markThicknessMm);
        }
    }

    public static CutProperties defaults() {
        return new CutProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Clean square needed at each corner: mark plus clearance (spec 10.5: 15 + 5 = 20 mm). */
    public double cornerZoneMm() {
        return markLengthMm + markClearanceMm;
    }

    /** Shape of the mark at one corner. */
    public MarkShape shapeAt(Corner corner) {
        return switch (corner) {
            case TOP_LEFT -> markTopLeft;
            case TOP_RIGHT -> markTopRight;
            case BOTTOM_LEFT -> markBottomLeft;
            case BOTTOM_RIGHT -> markBottomRight;
        };
    }

    /** Registration mark styles of Cutting Master (spec 10.5). */
    public enum MarkShape {
        /** Two bars on the two inner sides; the sheet edges close the square. */
        SQUARE_WITH_EDGE,
        /** Two bars along the sheet edges. */
        L,
        /** Four bars. */
        SQUARE_OUTLINE,
        /** Solid square. */
        SQUARE_FILLED
    }

    /** Page corners. */
    public enum Corner {
        BOTTOM_LEFT(false, false),
        BOTTOM_RIGHT(true, false),
        TOP_LEFT(false, true),
        TOP_RIGHT(true, true);

        private final boolean right;
        private final boolean top;

        Corner(boolean right, boolean top) {
            this.right = right;
            this.top = top;
        }

        public boolean right() {
            return right;
        }

        public boolean top() {
            return top;
        }
    }
}
