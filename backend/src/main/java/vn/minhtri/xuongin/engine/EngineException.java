package vn.minhtri.xuongin.engine;

import java.util.Map;

/** Engine failure with a Vietnamese message for the user. {@link Kind} names match the API error codes. */
public class EngineException extends RuntimeException {

    /** Maps 1:1 to the API's ErrorCode of the same name. */
    public enum Kind {
        INVALID_REQUEST,
        ITEM_WIDER_THAN_SHEET,
        NESTING_FAILED
    }

    private final Kind kind;
    private final transient Map<String, Object> details;

    public EngineException(Kind kind, String message) {
        this(kind, message, Map.of());
    }

    public EngineException(Kind kind, String message, Map<String, Object> details) {
        super(message);
        this.kind = kind;
        this.details = Map.copyOf(details);
    }

    public Kind kind() {
        return kind;
    }

    public Map<String, Object> details() {
        return details;
    }
}
