package vn.minhtri.xuongin.nesting;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.job.JobStatus;
import vn.minhtri.xuongin.job.JobStore;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiError;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.storage.FileStore;

/**
 * Creates nesting jobs and runs them on a dedicated bounded pool (spec 11). Every state change
 * stores a new immutable {@link Job}; every failure ends in a FAILED job, never in an exception
 * escaping the worker thread. Publishes {@link JobFinishedEvent} at the end.
 */
@Service
public class NestingJobService {

    /** Spec 6.4: RUNNING starts at 10; real engine progress fills 10..99, DONE is 100. */
    static final int RUNNING_START = 10;
    static final int RUNNING_END = 99;
    static final int QUEUE_CAPACITY = 100;

    private static final Logger log = LoggerFactory.getLogger(NestingJobService.class);

    private final JobStore jobs;
    private final FileStore files;
    private final NestingPlanner planner;
    private final ApplicationEventPublisher events;
    private final ThreadPoolExecutor executor;

    public NestingJobService(JobStore jobs, FileStore files, NestingPlanner planner, ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.files = files;
        this.planner = planner;
        this.events = events;
        int cores = Runtime.getRuntime().availableProcessors();
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(Math.max(2, cores / 2), Math.max(4, cores), 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), r -> {
                    Thread thread = new Thread(r, "nesting-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Validates synchronously (spec 6.3: FILE_NOT_FOUND, ITEM_WIDER_THAN_SHEET) and starts the job.
     *
     * @return the PENDING job
     */
    public Job create(NestRequest request) {
        Map<String, StoredFile> found = NestRequestValidator.requireFiles(request, id -> files.find(id).orElse(null));
        List<ResolvedItem> items = NestRequestValidator.resolve(request, found);
        NestRequestValidator.checkWidths(request, items);

        Job job = Job.pending(UUID.randomUUID().toString(), request);
        logParameters(job.jobId(), request, items);
        jobs.save(job);
        try {
            executor.execute(() -> run(job, found));
        } catch (RejectedExecutionException e) {
            String message = "Máy chủ đang bận với quá nhiều lần ghép. Hãy thử lại sau ít phút.";
            jobs.save(job.failed(new ApiError.Body(ErrorCode.INTERNAL_ERROR.name(), message, Map.of())));
            throw new ApiException(ErrorCode.INTERNAL_ERROR, message);
        }
        return job;
    }

    /** Runs one job to DONE or FAILED. Package-private for tests that run it synchronously. */
    void run(Job pending, Map<String, StoredFile> found) {
        long started = System.nanoTime();
        Job running = pending.running(RUNNING_START);
        jobs.save(running);
        AtomicInteger last = new AtomicInteger(RUNNING_START);
        Job finished;
        try {
            NestResult result = planner.plan(pending.request(), found, percent -> {
                int progress = RUNNING_START + Math.max(0, Math.min(100, percent)) * (RUNNING_END - RUNNING_START) / 100;
                if (progress > last.get()) {
                    last.set(progress);
                    jobs.save(running.running(progress));
                }
            });
            finished = running.done(result);
            log.info("Job {} done in {} ms: {} sheets, {} mm, fill {}", pending.jobId(), elapsedMs(started),
                    result.stats().totalSheets(), result.stats().totalLengthMm(), result.stats().fillRate());
        } catch (ApiException e) {
            finished = running.failed(new ApiError.Body(e.code().name(), e.getMessage(), e.details()));
            log.info("Job {} failed after {} ms: {} {}", pending.jobId(), elapsedMs(started), e.code(), e.getMessage());
        } catch (RuntimeException | Error e) {
            // Includes OutOfMemoryError: the job must still end FAILED instead of PENDING forever.
            finished = running.failed(new ApiError.Body(ErrorCode.NESTING_FAILED.name(),
                    "Thuật toán gặp lỗi không mong muốn: " + e.getMessage(), Map.of()));
            log.error("Job {} crashed after {} ms", pending.jobId(), elapsedMs(started), e);
        }
        jobs.save(finished);
        events.publishEvent(new JobFinishedEvent(finished));
    }

    /** @throws ApiException {@code JOB_NOT_FOUND} */
    public Job require(String jobId) {
        return jobs.find(jobId).orElseThrow(() -> new ApiException(ErrorCode.JOB_NOT_FOUND,
                "Không tìm thấy lần ghép " + jobId + ". Có thể máy chủ đã khởi động lại.", Map.of("jobId", jobId)));
    }

    /** @throws ApiException {@code JOB_NOT_FOUND}, or {@code JOB_NOT_READY} when the job is not DONE */
    public Job requireDone(String jobId) {
        Job job = require(jobId);
        if (job.status() != JobStatus.DONE) {
            throw new ApiException(ErrorCode.JOB_NOT_READY,
                    "Lần ghép này chưa có kết quả (trạng thái: " + statusLabel(job.status()) + ").",
                    Map.of("jobId", jobId, "status", job.status().name()));
        }
        return job;
    }

    /** @throws ApiException {@code JOB_NOT_FOUND} when the index is outside the job's sheets */
    public static Sheet requireSheet(Job job, int index) {
        List<Sheet> sheets = job.result().sheets();
        if (index < 0 || index >= sheets.size()) {
            throw new ApiException(ErrorCode.JOB_NOT_FOUND,
                    "Lần ghép này chỉ có " + sheets.size() + " tấm, không có tấm số " + (index + 1) + ".",
                    Map.of("jobId", job.jobId(), "sheetIndex", index));
        }
        return sheets.get(index);
    }

    private static String statusLabel(JobStatus status) {
        return switch (status) {
            case PENDING -> "đang chờ";
            case RUNNING -> "đang ghép";
            case FAILED -> "lỗi";
            case DONE -> "xong";
        };
    }

    /** Spec 6.3: log exactly the received parameters, to answer "did the UI send the new value". */
    private static void logParameters(String jobId, NestRequest r, List<ResolvedItem> items) {
        int pieces = items.stream().mapToInt(ResolvedItem::quantity).sum();
        long types = items.stream().map(ResolvedItem::fileId).distinct().count();
        log.info(String.format(Locale.ROOT,
                "Job %s created: mode=%s sheetWidthMm=%s marginMm=%s gapMm=%s maxSheetLengthMm=%s "
                        + "allowRotateGlobal=%s drawCutLines=%s types=%d pieces=%d",
                jobId, r.modeOrDefault(), r.sheetWidthMm(), r.marginMm(), r.gapMm(), r.maxSheetLengthMm(),
                r.allowRotateGlobal(), r.drawCutLinesOrDefault(), types, pieces));
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
