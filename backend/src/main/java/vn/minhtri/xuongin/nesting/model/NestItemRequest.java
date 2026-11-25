package vn.minhtri.xuongin.nesting.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** One design in the order. widthMm/heightMm override the size read from the file. */
public record NestItemRequest(
        @NotBlank(message = "Thiếu mã file.") String fileId,
        @Min(value = 1, message = "Số lượng tối thiểu là 1.") int quantity,
        boolean allowRotate,
        @Positive(message = "Chiều rộng phải lớn hơn 0.") Double widthMm,
        @Positive(message = "Chiều cao phải lớn hơn 0.") Double heightMm) {}
