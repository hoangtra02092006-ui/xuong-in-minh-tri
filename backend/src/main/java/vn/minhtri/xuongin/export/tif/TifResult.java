package vn.minhtri.xuongin.export.tif;

import java.nio.file.Path;

/** A finished TIF on disk. */
public record TifResult(Path path, long sizeBytes, int width, int height, int dpi) {}
