package vn.minhtri.xuongin.file;

/**
 * What the readers measure from a source file, before it becomes a {@link StoredFile}.
 * Sizes are in mm, unrounded, already swapped for a PDF {@code /Rotate} of 90 / 270.
 */
record SourceMetadata(
        FileType type,
        double widthMm,
        double heightMm,
        double sourceWidthMm,
        double sourceHeightMm,
        ContentBox contentBox,
        OccupancyMask occupancy,
        int pageRotation,
        int pageCount,
        boolean hasAlpha) {}
