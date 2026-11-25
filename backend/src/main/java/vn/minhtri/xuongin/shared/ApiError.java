package vn.minhtri.xuongin.shared;

import java.util.Map;

/** Uniform error body: {@code {"error":{"code","message","details"}}}. Also embedded in FAILED jobs. */
public record ApiError(Body error) {

    public record Body(String code, String message, Map<String, Object> details) {}

    public static ApiError of(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiError(new Body(code.name(), message, details));
    }
}
