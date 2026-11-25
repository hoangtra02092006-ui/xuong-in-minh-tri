package vn.minhtri.xuongin.engine;

import java.util.Objects;
import vn.minhtri.xuongin.file.OccupancyMask;

/**
 * One design to pack, sizes already resolved (user override or file metadata).
 *
 * @param id            stable id of the design (the fileId); used for reporting
 * @param label         display name
 * @param categoryIndex colour index (order of first appearance of the fileId)
 * @param w             real width before rotation, 1/100 mm
 * @param h             real height before rotation, 1/100 mm
 * @param quantity      number of prints, at least 1
 * @param allowRotate   the row's own rotate flag (the engine combines it with the global switch)
 * @param shape         where the artwork really is inside the box, or null = solid box. Pass it only when
 *                      spec 8.4 allows (vector PDF, size not edited by hand, no /Rotate); otherwise null.
 *                      Cell (c, r) covers [c*cellMm, (c+1)*cellMm] from the box's bottom-left; parts of the
 *                      box the grid does not cover count as artwork. Used by FREE and TRUE_SHAPE only.
 */
public record EngineItem(
        String id,
        String label,
        int categoryIndex,
        long w,
        long h,
        int quantity,
        boolean allowRotate,
        OccupancyMask shape) {

    public EngineItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + w + "x" + h);
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1: " + quantity);
        }
    }

    /** Solid design without a shape mask. */
    public EngineItem(String id, String label, int categoryIndex, long w, long h, int quantity, boolean allowRotate) {
        this(id, label, categoryIndex, w, h, quantity, allowRotate, null);
    }
}
