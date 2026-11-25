package vn.minhtri.xuongin.shared;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /** e.g. a sheet index that is not a number in {@code /sheets/abc/pdf}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return body(ErrorCode.INVALID_REQUEST, "Tham số \"" + e.getName() + "\" không hợp lệ.", Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(ErrorCode.FILE_TOO_LARGE, "File vượt quá dung lượng cho phép (50 MB mỗi file, 200 MB mỗi lần tải).", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return body(ErrorCode.INVALID_REQUEST, "Không có đường dẫn này.", Map.of());
    }

    /** e.g. PUT on /nesting/jobs. The contract has no 405 code: a wrong call is INVALID_REQUEST. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.debug("Method not supported: {}", e.getMessage());
        return body(ErrorCode.INVALID_REQUEST, "Đường dẫn này không nhận phương thức " + e.getMethod() + ".", Map.of());
    }

    /** e.g. a JSON endpoint called with Content-Type text/plain. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.debug("Media type not supported: {}", e.getMessage());
        String type = e.getContentType() == null ? "trống" : e.getContentType().toString();
        return body(ErrorCode.INVALID_REQUEST, "Kiểu dữ liệu gửi lên (" + type + ") không hỗ trợ.", Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e) {
        return body(ErrorCode.INVALID_REQUEST, "Thiếu tham số \"" + e.getParameterName() + "\".", Map.of());
    }

    /** POST /files that is not multipart, or a broken multipart body. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<ApiError> handleMultipart(Exception e) {
        log.debug("Bad multipart request: {}", e.getMessage());
        return body(ErrorCode.INVALID_REQUEST, "Dữ liệu tải lên không đúng định dạng (cần multipart/form-data).",
                Map.of());
    }

    /**
     * A streamed response (zip) or a long wait ran past APP_EXPORT_TIMEOUT_MS. Nothing can be
     * written once the response started; otherwise the client may simply try again.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    ResponseEntity<ApiError> handleAsyncTimeout(AsyncRequestTimeoutException e, HttpServletResponse response) {
        log.warn("Async request timed out (APP_EXPORT_TIMEOUT_MS)");
        if (response.isCommitted()) {
            return null;
        }
        return body(ErrorCode.JOB_NOT_READY, "Máy chủ chưa chuẩn bị xong file trong thời hạn chờ. Hãy thử tải lại sau"
                + " ít phút.", Map.of());
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
