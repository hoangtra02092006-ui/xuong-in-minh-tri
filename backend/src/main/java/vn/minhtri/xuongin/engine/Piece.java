package vn.minhtri.xuongin.engine;

import java.util.List;

/**
 * One print to pack, inflated by one gap in both directions (spec 8.1).
 *
 * @param id          unique id in the run, final tie-break of every sort
 * @param itemIndex   index of its design in the input list
 * @param w           width incl. gap, 1/100 mm
 * @param h           height incl. gap
 * @param realW       real width
 * @param realH       real height
 * @param allowRotate global switch AND row flag
 * @param prerotated  w/h swapped before packing (forced-orientation variant)
 * @param cavities    empty rectangles inside the real box (FREE mode), in the current orientation
 */
record Piece(
        int id,
        int itemIndex,
        long w,
        long h,
        long realW,
        long realH,
        boolean allowRotate,
        boolean prerotated,
        List<Cavity> cavities) {

    Piece rotated90() {
        List<Cavity> turned = cavities.stream().map(c -> c.rotated90(realH)).toList();
        return new Piece(id, itemIndex, h, w, realH, realW, allowRotate, !prerotated, turned);
    }

    long area() {
        return w * h;
    }

    /** Box area minus cavities: the lower bound of the length search must not count space others can use. */
    long netArea() {
        long used = area();
        for (Cavity cavity : cavities) {
            used -= cavity.area();
        }
        return Math.max(0, used);
    }

    long longSide() {
        return Math.max(w, h);
    }

    long shortSide() {
        return Math.min(w, h);
    }
}
