package vn.minhtri.xuongin.shared;

import org.springframework.http.HttpStatus;

/** Error codes of the public API (spec section 6.6). The frontend branches on these, never on the message. */
public enum ErrorCode {
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    SIZE_UNREADABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    ITEM_WIDER_THAN_SHEET(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    JOB_NOT_READY(HttpStatus.CONFLICT),
    NESTING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    TIFF_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
