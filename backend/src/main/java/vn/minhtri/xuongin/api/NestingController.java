package vn.minhtri.xuongin.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.minhtri.xuongin.export.SheetTif;
import vn.minhtri.xuongin.export.TifPrebuildService;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.nesting.NestingJobService;
import vn.minhtri.xuongin.nesting.model.NestRequest;

/** Create and poll nesting jobs (spec 6.3, 6.4) and move a sheet's TIF to the front (design 4a). */
@RestController
@RequestMapping(NestingController.BASE_PATH)
public class NestingController {

    public static final String BASE_PATH = "/api/v1/nesting/jobs";

    private final NestingJobService jobs;
    private final TifPrebuildService tifs;

    public NestingController(NestingJobService jobs, TifPrebuildService tifs) {
        this.jobs = jobs;
        this.tifs = tifs;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody NestRequest request) {
        Job job = jobs.create(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.of(job, null));
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable String jobId) {
        Job job = jobs.require(jobId);
        List<SheetTif> states = tifs.statuses(jobId).orElse(null);
        return JobResponse.of(job, states);
    }

    /** Builds this sheet's TIF next; answers the sheet's TIF state. */
    @PostMapping("/{jobId}/sheets/{index}/tif/prioritize")
    public SheetTif prioritize(@PathVariable String jobId, @PathVariable int index) {
        Job job = jobs.requireDone(jobId);
        NestingJobService.requireSheet(job, index);
        return tifs.prioritize(job, index);
    }
}
