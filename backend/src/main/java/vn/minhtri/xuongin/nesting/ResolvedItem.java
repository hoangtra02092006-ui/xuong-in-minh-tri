package vn.minhtri.xuongin.nesting;

import vn.minhtri.xuongin.file.OccupancyMask;

/**
 * One request row with its size settled (spec 8.1): the user's override, else the file metadata.
 *
 * @param categoryIndex order of first appearance of the fileId in the request
 * @param allowRotate   {@code allowRotateGlobal && item.allowRotate} (invariant 8)
 * @param shape         artwork mask, only when spec 8.4 allows it (vector PDF, size not edited, no
 *                      /Rotate); null = solid box
 */
public record ResolvedItem(
        String fileId,
        String label,
        int categoryIndex,
        double widthMm,
        double heightMm,
        int quantity,
        boolean allowRotate,
        OccupancyMask shape) {}
