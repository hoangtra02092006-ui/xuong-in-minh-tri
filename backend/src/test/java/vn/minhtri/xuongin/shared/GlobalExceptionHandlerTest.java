package vn.minhtri.xuongin.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsCodeMessageAndDetails() {
        var res = handler.handleApi(new ApiException(ErrorCode.ITEM_WIDER_THAN_SHEET, "Hình rộng hơn khổ", Map.of("fileId", "f1")));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().error().code()).isEqualTo("ITEM_WIDER_THAN_SHEET");
        assertThat(res.getBody().error().message()).isEqualTo("Hình rộng hơn khổ");
        assertThat(res.getBody().error().details()).containsEntry("fileId", "f1");
    }

    @Test
    void validationUsesFirstFieldMessage() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "gapMm", "Khoảng cách không được âm."));
        var param = new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("validationUsesFirstFieldMessage"), -1);
        var res = handler.handleValidation(new MethodArgumentNotValidException(param, binding));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().error().message()).isEqualTo("Khoảng cách không được âm.");
    }

    @Test
    void unreadableBodyIsInvalidRequest() {
        var res = handler.handleUnreadable(new HttpMessageNotReadableException("x", new MockHttpInputMessage(new byte[0])));
        assertThat(res.getBody().error().code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void uploadTooLargeIs413() {
        var res = handler.handleTooLarge(new MaxUploadSizeExceededException(1));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody().error().code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void unexpectedErrorHidesDetails() {
        var res = handler.handleUnexpected(new IllegalStateException("secret stack"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(res.getBody().error().message()).doesNotContain("secret");
    }
}
