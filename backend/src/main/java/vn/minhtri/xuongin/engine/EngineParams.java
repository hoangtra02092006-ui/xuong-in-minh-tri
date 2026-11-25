package vn.minhtri.xuongin.engine;

import java.util.Objects;

/**
 * Sheet parameters, all lengths in 1/100 mm.
 *
 * @param mode              packing mode
 * @param sheetWidth        roll width
 * @param margin            empty border on all four sides
 * @param gap               minimum distance between any two prints
 * @param maxSheetLength    maximum length of one sheet including margins, or null = one unlimited sheet
 * @param allowRotateGlobal global rotate switch; false overrides every row
 */
public record EngineParams(
        EngineMode mode, long sheetWidth, long margin, long gap, Long maxSheetLength, boolean allowRotateGlobal) {

    public EngineParams {
        Objects.requireNonNull(mode, "mode");
        if (sheetWidth <= 0 || margin < 0 || gap < 0 || (maxSheetLength != null && maxSheetLength <= 0)) {
            throw new IllegalArgumentException("Invalid sheet parameters");
        }
    }
}
