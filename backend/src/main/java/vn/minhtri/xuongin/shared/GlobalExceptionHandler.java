package vn.minhtri.xuongin.shared;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Renders every error as {@link ApiError}. Stack traces go to the log only. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        return body(e.code(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        String message = first != null ? first.getDefaultMessage() : "Tham số không hợp lệ.";
        return body(ErrorCode.INVALID_REQUEST, message, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return body(ErrorCode.INVALID_REQUEST, "Dữ liệu gửi lên không đúng định dạng.", Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(ErrorCode.FILE_TOO_LARGE, "File vượt quá dung lượng cho phép (50 MB mỗi file, 200 MB mỗi lần tải).", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return body(ErrorCode.INVALID_REQUEST, "Không có đường dẫn này.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return body(ErrorCode.INTERNAL_ERROR, "Máy chủ gặp lỗi không mong muốn. Hãy thử lại.", Map.of());
    }

    private static ResponseEntity<ApiError> body(ErrorCode code, String message, Map<String, Object> details) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, message, details));
    }
}
