package vn.minhtri.xuongin.shared;

/**
 * The only place where units are converted (invariant 7).
 * API: millimetres (max 2 decimals). Engine: integer hundredths of a millimetre. PDF: points.
 */
public final class Units {

    public static final double POINTS_PER_MM = 72.0 / 25.4;

    private Units() {}

    /** mm (API) to integer 1/100 mm (engine), rounded half up. */
    public static long mmToCenti(double mm) {
        return Math.round(mm * 100.0);
    }

    /** Integer 1/100 mm (engine) to mm (API). */
    public static double centiToMm(long centi) {
        return centi / 100.0;
    }

    public static double mmToPt(double mm) {
        return mm * POINTS_PER_MM;
    }

    public static double ptToMm(double pt) {
        return pt / POINTS_PER_MM;
    }

    /** Rounds to 2 decimals for API output. */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
