package vn.minhtri.xuongin.export;

import vn.minhtri.xuongin.shared.ApiError;

/**
 * Background TIF state of one sheet, as {@code result.sheets[].tif} in the job response (design 4a).
 *
 * @param sizeBytes file size once READY, else null
 * @param error     reason once FAILED (same body as API errors, Vietnamese message), else null
 */
public record SheetTif(Status status, Long sizeBytes, ApiError.Body error) {

    public enum Status {
        QUEUED,
        BUILDING,
        READY,
        FAILED
    }

    static final SheetTif QUEUED = new SheetTif(Status.QUEUED, null, null);
    static final SheetTif BUILDING = new SheetTif(Status.BUILDING, null, null);

    static SheetTif ready(long sizeBytes) {
        return new SheetTif(Status.READY, sizeBytes, null);
    }

    static SheetTif failed(ApiError.Body error) {
        return new SheetTif(Status.FAILED, null, error);
    }
}
