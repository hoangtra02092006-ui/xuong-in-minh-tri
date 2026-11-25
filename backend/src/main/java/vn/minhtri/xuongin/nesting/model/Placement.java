package vn.minhtri.xuongin.nesting.model;

/**
 * Where one piece sits on a sheet (mm, origin at the sheet's bottom-left).
 * x/y is the bottom-left of the bounding box AFTER rotation; w/h is that box.
 * rotated = exactly 90 degrees; angleDeg = free angle (TRUE_SHAPE only, then rotated = false).
 * sourceW/H = size before rotation.
 */
public record Placement(
        String fileId,
        String label,
        int categoryIndex,
        double xMm,
        double yMm,
        double wMm,
        double hMm,
        boolean rotated,
        double angleDeg,
        double sourceWMm,
        double sourceHMm) {}
