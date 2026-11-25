package vn.minhtri.xuongin.export;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import vn.minhtri.xuongin.export.tif.TifResult;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.job.JobStatus;
import vn.minhtri.xuongin.job.JobStore;
import vn.minhtri.xuongin.nesting.JobFinishedEvent;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.raster.RasterMemoryGuard;
import vn.minhtri.xuongin.shared.ApiError;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;

/**
 * Builds the TIF of every finished job in the background (design 4a): ONE worker thread, one
 * sheet at a time (RAM cap of the shared server), sheets from 1 up, files on disk. A download or
 * {@code prioritize} moves a sheet to the front; a failed sheet is retried when asked again.
 * Runs server-side, so closing the browser tab changes nothing.
 */
@Service
public class TifPrebuildService {

    private static final Logger log = LoggerFactory.getLogger(TifPrebuildService.class);
    private static final long POLL_MS = 1_000;

    private final JobStore jobs;
    private final ExportStore store;
    private final SheetTifBuilder builder;
    private final TifQueue queue = new TifQueue();
    private final Map<String, Slot[]> slots = new ConcurrentHashMap<>();
    private volatile Thread worker;

    /** State plus a future that completes with the file when READY, exceptionally when FAILED. */
    private record Slot(SheetTif state, CompletableFuture<Path> done) {
        static Slot queued() {
            return new Slot(SheetTif.QUEUED, new CompletableFuture<>());
        }
    }

    public TifPrebuildService(JobStore jobs, ExportStore store, SheetTifBuilder builder) {
        this.jobs = jobs;
        this.store = store;
        this.builder = builder;
        store.onPurge(this::forget);
    }

    @PostConstruct
    public synchronized void start() {
        if (worker != null) {
            return;
        }
        Thread thread = new Thread(this::loop, "tif-prebuild");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    @PreDestroy
    public synchronized void stop() {
        Thread thread = worker;
        worker = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Package-private view of the build order, for tests. */
    TifQueue queue() {
        return queue;
    }

    @EventListener
    public void onJobFinished(JobFinishedEvent event) {
        if (event.job().status() == JobStatus.DONE) {
            register(event.job());
        }
    }

    /** Queues every sheet of a DONE job (no-op when already known). */
    public void register(Job job) {
        int count = job.result().sheets().size();
        Slot[] created = new Slot[count];
        for (int i = 0; i < count; i++) {
            created[i] = Slot.queued();
        }
        if (slots.putIfAbsent(job.jobId(), created) == null) {
            queue.enqueueJob(job.jobId(), count);
        }
    }

    /** TIF state of each sheet; empty when the job is unknown here (not DONE, or purged). */
    public Optional<List<SheetTif>> statuses(String jobId) {
        Slot[] job = slots.get(jobId);
        if (job == null) {
            return Optional.empty();
        }
        synchronized (job) {
            List<SheetTif> states = new ArrayList<>(job.length);
            for (Slot slot : job) {
                states.add(slot.state());
            }
            return Optional.of(states);
        }
    }

    /**
     * Builds this sheet next. A FAILED sheet (or a READY one whose file vanished) is queued again;
     * a READY or BUILDING sheet is left alone.
     */
    public SheetTif prioritize(Job job, int index) {
        register(job);
        Slot[] all = slots.get(job.jobId());
        if (all == null) {
            // Purged between register and here: nothing to build any more.
            throw new ApiException(ErrorCode.JOB_NOT_FOUND,
                    "Không tìm thấy lần ghép " + job.jobId() + ". Có thể máy chủ đã khởi động lại.");
        }
        synchronized (all) {
            Slot slot = all[index];
            SheetTif.Status status = slot.state().status();
            boolean vanished = status == SheetTif.Status.READY && !Files.exists(store.tifPath(job.jobId(), index));
            if (status == SheetTif.Status.FAILED || vanished) {
                slot = Slot.queued();
                all[index] = slot;
                queue.prioritize(job.jobId(), index);
            } else if (status == SheetTif.Status.QUEUED) {
                queue.prioritize(job.jobId(), index);
            }
            return slot.state();
        }
    }

    /**
     * The sheet's TIF file, building it first (at the front of the queue) when needed.
     *
     * @throws ApiException the build's own error (e.g. {@code TIFF_TOO_LARGE}), or {@code JOB_NOT_READY}
     *                      when it is not ready within {@code timeoutMs}
     */
    public Path await(Job job, int index, long timeoutMs) {
        prioritize(job, index);
        CompletableFuture<Path> done;
        Slot[] all = slots.get(job.jobId());
        if (all == null) {
            throw new ApiException(ErrorCode.JOB_NOT_FOUND, "Lần ghép " + job.jobId() + " đã bị dọn sau thời hạn lưu.");
        }
        synchronized (all) {
            done = all[index].done();
        }
        try {
            return done.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ApiException(ErrorCode.JOB_NOT_READY, "File TIF của tấm " + (index + 1)
                    + " vẫn đang được dựng. Hãy thử tải lại sau ít phút.", Map.of("sheetIndex", index));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Máy chủ đã dừng khi đang chờ file TIF.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException api) {
                throw new ApiException(api.code(), api.getMessage(), api.details());
            }
            throw new ApiException(ErrorCode.INTERNAL_ERROR, failureMessage(index, e.getCause()));
        }
    }

    /** Drops the state and waiting sheets of a purged job. */
    public void forget(String jobId) {
        queue.removeJob(jobId);
        Slot[] removed = slots.remove(jobId);
        if (removed != null) {
            synchronized (removed) {
                for (Slot slot : removed) {
                    slot.done().completeExceptionally(new ApiException(ErrorCode.JOB_NOT_FOUND,
                            "Lần ghép " + jobId + " đã bị dọn sau thời hạn lưu."));
                }
            }
        }
    }

    private void loop() {
        while (worker == Thread.currentThread()) {
            try {
                runOnce(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * Builds the next queued sheet, waiting at most {@code timeoutMs} for one.
     *
     * @return false when nothing was queued
     */
    boolean runOnce(long timeoutMs) throws InterruptedException {
        TifQueue.Task task = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (task == null) {
            return false;
        }
        Slot[] all = slots.get(task.jobId());
        if (all == null) {
            return true;
        }
        Slot slot;
        synchronized (all) {
            slot = all[task.index()];
            if (slot.state().status() != SheetTif.Status.QUEUED) {
                return true;
            }
            slot = new Slot(SheetTif.BUILDING, slot.done());
            all[task.index()] = slot;
        }
        build(task, all, slot.done());
        return true;
    }

    private void build(TifQueue.Task task, Slot[] all, CompletableFuture<Path> done) {
        SheetTif outcome;
        Path path = null;
        Throwable failure = null;
        try {
            Job job = jobs.find(task.jobId()).orElseThrow(() -> new ApiException(ErrorCode.JOB_NOT_FOUND,
                    "Không tìm thấy lần ghép " + task.jobId() + ". Có thể máy chủ đã khởi động lại."));
            Sheet sheet = job.result().sheets().get(task.index());
            TifResult result = builder.build(job, sheet, store.tifPath(job.jobId(), task.index()));
            path = result.path();
            outcome = SheetTif.ready(result.sizeBytes());
        } catch (OutOfMemoryError e) {
            ApiException mapped = RasterMemoryGuard.outOfMemory(task.index(), "TIF");
            failure = mapped;
            outcome = SheetTif.failed(new ApiError.Body(mapped.code().name(), mapped.getMessage(), mapped.details()));
            log.warn("TIF of job {} sheet {} ran out of memory", task.jobId(), task.index() + 1);
        } catch (ApiException e) {
            failure = e;
            outcome = SheetTif.failed(new ApiError.Body(e.code().name(), e.getMessage(), e.details()));
            log.warn("TIF of job {} sheet {} failed: {} {}", task.jobId(), task.index() + 1, e.code(), e.getMessage());
        } catch (Exception | Error e) {
            // The worker must survive and the sheet must say FAILED.
            failure = e;
            outcome = SheetTif.failed(new ApiError.Body(ErrorCode.INTERNAL_ERROR.name(),
                    failureMessage(task.index(), e), Map.of("sheetIndex", task.index())));
            log.error("TIF of job {} sheet {} crashed", task.jobId(), task.index() + 1, e);
        }
        synchronized (all) {
            if (all[task.index()].done() == done) {
                all[task.index()] = new Slot(outcome, done);
            }
        }
        if (failure == null) {
            done.complete(path);
        } else {
            done.completeExceptionally(failure);
        }
    }

    private static String failureMessage(int index, Throwable e) {
        return "Không dựng được file TIF tấm " + (index + 1) + ": " + (e == null ? "lỗi không rõ" : e.getMessage());
    }
}
