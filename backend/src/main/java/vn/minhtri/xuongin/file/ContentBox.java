package vn.minhtri.xuongin.file;

/**
 * Area that actually contains artwork, in the source page's own coordinate system
 * (PDF points, origin bottom-left). Measured once at upload; export MUST draw with this
 * same box (invariant 9), never with {@code form.getBBox()}.
 */
public record ContentBox(double xPt, double yPt, double widthPt, double heightPt) {}
