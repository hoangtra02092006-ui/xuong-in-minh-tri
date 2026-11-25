package vn.minhtri.xuongin.engine;

/**
 * Where one print sits, 1/100 mm, origin at the sheet's bottom-left (margins included).
 *
 * <p>Rotation is counter-clockwise: a source point (u, v) goes to (u cos a - v sin a, u sin a + v cos a),
 * then the rotated source box is moved so that its bounding box's bottom-left is (x, y).
 * For 90 degrees that is (u, v) -> (x + srcH - v, y + u).
 *
 * @param itemIndex index of the design in the engine's input list
 * @param pieceId   unique id of this print within the run (item order x quantity)
 * @param x         left of the bounding box after rotation
 * @param y         bottom of the bounding box after rotation
 * @param w         bounding box width after rotation
 * @param h         bounding box height after rotation
 * @param rotated   exactly 90 degrees (ORTHOGONAL / FREE)
 * @param angleDeg  free angle (TRUE_SHAPE only, then rotated = false); 0 otherwise
 */
public record EnginePlacement(
        int itemIndex, int pieceId, long x, long y, long w, long h, boolean rotated, int angleDeg) {

    public long right() {
        return x + w;
    }

    public long top() {
        return y + h;
    }

    /** Total counter-clockwise rotation in degrees, whatever mode produced it. */
    public int totalAngleDeg() {
        return rotated ? 90 : angleDeg;
    }
}
