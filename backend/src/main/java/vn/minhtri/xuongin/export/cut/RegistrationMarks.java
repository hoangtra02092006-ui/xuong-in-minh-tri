package vn.minhtri.xuongin.export.cut;

import java.util.ArrayList;
import java.util.List;
import vn.minhtri.xuongin.export.cut.CutProperties.Corner;
import vn.minhtri.xuongin.export.cut.CutProperties.MarkShape;

/**
 * Geometry of the four registration marks (spec 10.5): the mark's outer corner sits exactly on
 * the page corner, no margin. Each mark is a set of filled rectangles, never stroked lines, so
 * the thickness is the real size whatever the viewer does with line widths.
 */
final class RegistrationMarks {

    /** Filled rectangle in page mm, origin bottom-left. */
    record Rect(double x, double y, double w, double h) {}

    private RegistrationMarks() {}

    /** Rectangles of the mark at {@code corner} of a {@code pageW × pageL} mm page. */
    static List<Rect> at(Corner corner, MarkShape shape, double pageW, double pageL, double length, double thickness) {
        // Bottom-left of the mark's square, and of the inner bars.
        double left = corner.right() ? pageW - length : 0;
        double bottom = corner.top() ? pageL - length : 0;
        double innerX = corner.right() ? left : left + length - thickness;
        double innerY = corner.top() ? bottom : bottom + length - thickness;
        double edgeX = corner.right() ? pageW - thickness : 0;
        double edgeY = corner.top() ? pageL - thickness : 0;

        List<Rect> rects = new ArrayList<>(4);
        switch (shape) {
            case SQUARE_WITH_EDGE -> {
                rects.add(new Rect(innerX, bottom, thickness, length));
                rects.add(new Rect(left, innerY, length, thickness));
            }
            case L -> {
                rects.add(new Rect(edgeX, bottom, thickness, length));
                rects.add(new Rect(left, edgeY, length, thickness));
            }
            case SQUARE_OUTLINE -> {
                rects.add(new Rect(left, bottom, length, thickness));
                rects.add(new Rect(left, bottom + length - thickness, length, thickness));
                rects.add(new Rect(left, bottom, thickness, length));
                rects.add(new Rect(left + length - thickness, bottom, thickness, length));
            }
            case SQUARE_FILLED -> rects.add(new Rect(left, bottom, length, length));
        }
        return rects;
    }
}
