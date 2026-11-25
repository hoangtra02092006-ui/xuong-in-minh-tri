package vn.minhtri.xuongin.nesting.model;

import java.util.List;

/** One exported file. fillRate = union of bounding boxes / (width x length), 4 decimals. */
public record Sheet(int index, double widthMm, double lengthMm, double fillRate, List<Placement> placements) {}
