package vn.minhtri.xuongin.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import vn.minhtri.xuongin.export.SheetTif;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.job.JobStatus;
import vn.minhtri.xuongin.nesting.model.NestStats;
import vn.minhtri.xuongin.nesting.model.Placement;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiError;

/**
 * Job as the API shows it (spec 6.3, 6.4, design 4a). The original request is never returned:
 * the job is polled every 700 ms. Absent fields are left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(
        String jobId,
        JobStatus status,
        int progress,
        Instant createdAt,
        Instant finishedAt,
        Result result,
        ApiError.Body error) {

    public record Result(List<SheetView> sheets, NestStats stats) {}

    /** {@link Sheet} plus its background TIF state. */
    public record SheetView(int index, double widthMm, double lengthMm, double fillRate, List<Placement> placements,
            SheetTif tif) {}

    /** Shown while the job has no TIF state yet (it is queued as soon as the job is DONE). */
    static final SheetTif QUEUED = new SheetTif(SheetTif.Status.QUEUED, null, null);

    /** @param tifs TIF state per sheet, or null when unknown (each sheet then shows QUEUED) */
    public static JobResponse of(Job job, List<SheetTif> tifs) {
        Result result = null;
        if (job.result() != null) {
            List<SheetView> sheets = new ArrayList<>();
            for (Sheet s : job.result().sheets()) {
                SheetTif tif = tifs != null && s.index() < tifs.size() ? tifs.get(s.index()) : QUEUED;
                sheets.add(new SheetView(s.index(), s.widthMm(), s.lengthMm(), s.fillRate(), s.placements(), tif));
            }
            result = new Result(sheets, job.result().stats());
        }
        return new JobResponse(job.jobId(), job.status(), job.progress(), job.createdAt(), job.finishedAt(), result,
                job.error());
    }
}
