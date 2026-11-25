package vn.minhtri.xuongin.nesting;

import java.util.Map;
import java.util.function.IntConsumer;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.nesting.model.NestResult;

/** Port between the job runner and the packing algorithm. */
public interface NestingPlanner {

    /**
     * Packs the request.
     *
     * @param files           every file of the request, by id (already checked to exist)
     * @param progressPercent receives real progress 0..100, may be called often and from the planner's thread
     * @throws vn.minhtri.xuongin.shared.ApiException with the spec 6.3 code and message for engine errors
     */
    NestResult plan(NestRequest req, Map<String, StoredFile> files, IntConsumer progressPercent);
}
