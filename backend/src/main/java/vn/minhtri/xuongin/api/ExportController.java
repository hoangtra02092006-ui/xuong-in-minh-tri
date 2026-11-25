package vn.minhtri.xuongin.api;

import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.minhtri.xuongin.export.ExportFormat;
import vn.minhtri.xuongin.export.ExportService;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.nesting.NestingJobService;
import vn.minhtri.xuongin.nesting.model.Sheet;

/** Downloads (spec 6.5): one sheet as PDF, TIF or cut file, or every sheet of a format as a streamed zip. */
@RestController
@RequestMapping(NestingController.BASE_PATH)
public class ExportController {

    private final NestingJobService jobs;
    private final ExportService exports;

    public ExportController(NestingJobService jobs, ExportService exports) {
        this.jobs = jobs;
        this.exports = exports;
    }

    @GetMapping("/{jobId}/sheets/{index}/pdf")
    public ResponseEntity<Resource> pdf(@PathVariable String jobId, @PathVariable int index) {
        return sheet(jobId, index, ExportFormat.PDF);
    }

    /** Waits (bounded) for a TIF still queued or building; it is moved to the front of the queue first. */
    @GetMapping("/{jobId}/sheets/{index}/tif")
    public ResponseEntity<Resource> tif(@PathVariable String jobId, @PathVariable int index) {
        return sheet(jobId, index, ExportFormat.TIF);
    }

    @GetMapping("/{jobId}/sheets/{index}/cut")
    public ResponseEntity<Resource> cut(@PathVariable String jobId, @PathVariable int index) {
        return sheet(jobId, index, ExportFormat.CUT);
    }

    @GetMapping("/{jobId}/export.zip")
    public ResponseEntity<StreamingResponseBody> zip(@PathVariable String jobId,
            @RequestParam(name = "format", required = false) String format) {
        ExportFormat parsed = ExportFormat.parse(format);
        Job job = jobs.requireDone(jobId);
        exports.checkAvailable(parsed);
        StreamingResponseBody body = out -> exports.zip(job, parsed, out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(parsed.zipName(job.jobId())))
                .body(body);
    }

    private ResponseEntity<Resource> sheet(String jobId, int index, ExportFormat format) {
        Job job = jobs.requireDone(jobId);
        Sheet sheet = NestingJobService.requireSheet(job, index);
        Path file = exports.file(job, sheet, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(format.fileName(job.jobId(), sheet)))
                .body(new FileSystemResource(file));
    }

    private static String attachment(String name) {
        return ContentDisposition.attachment().filename(name).build().toString();
    }
}
