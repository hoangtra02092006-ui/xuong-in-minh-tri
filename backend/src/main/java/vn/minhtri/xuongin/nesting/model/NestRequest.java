package vn.minhtri.xuongin.nesting.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** POST /api/v1/nesting/jobs body (spec 6.3). All lengths in mm. */
public record NestRequest(
        NestingMode mode,
        @Positive(message = "Khổ ngang phải lớn hơn 0.") double sheetWidthMm,
        @PositiveOrZero(message = "Lề biên không được âm.") double marginMm,
        @PositiveOrZero(message = "Khoảng cách không được âm.") double gapMm,
        @Positive(message = "Chiều dài tối đa mỗi file phải lớn hơn 0.") Double maxSheetLengthMm,
        boolean allowRotateGlobal,
        Boolean drawCutLines,
        @NotEmpty(message = "Danh sách hình không được rỗng.") @Valid List<NestItemRequest> items) {

    public NestingMode modeOrDefault() {
        return mode == null ? NestingMode.ORTHOGONAL : mode;
    }

    /** Missing = true (spec 6.3); the frontend always sends it. */
    public boolean drawCutLinesOrDefault() {
        return drawCutLines == null || drawCutLines;
    }
}
