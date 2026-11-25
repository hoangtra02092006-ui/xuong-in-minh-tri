package vn.minhtri.xuongin.export;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Order in which sheets get their TIF built (design 4a): sheets someone asked for first (most
 * recent request first), then the NEWEST finished job as a block, sheets from 1 up, then the
 * pending sheets of older jobs. The newest job is the one the worker is looking at; an abandoned
 * old job must not hold it up. Thread-safe; one consumer.
 */
public final class TifQueue {

    /** One sheet of one job. */
    public record Task(String jobId, int index) {}

    /** A task taken from the queue, and whether someone asked for it ({@link #prioritize}). */
    public record Next(Task task, boolean requested) {}

    private final List<Task> tasks = new ArrayList<>();
    private final Set<Task> requested = new HashSet<>();

    /**
     * Puts sheets {@code 0..count-1} of a newly finished job right after the requested sheets,
     * ahead of older jobs. Sheets already waiting keep their place.
     */
    public synchronized void enqueueJob(String jobId, int count) {
        int at = 0;
        while (at < tasks.size() && requested.contains(tasks.get(at))) {
            at++;
        }
        for (int i = 0; i < count; i++) {
            Task task = new Task(jobId, i);
            if (!tasks.contains(task)) {
                tasks.add(at++, task);
            }
        }
        notifyAll();
    }

    /** Puts the sheet at the very front, whether it was waiting further back or not queued at all. */
    public synchronized void prioritize(String jobId, int index) {
        Task task = new Task(jobId, index);
        tasks.remove(task);
        tasks.add(0, task);
        requested.add(task);
        notifyAll();
    }

    /** Drops every waiting sheet of a job (job purged). */
    public synchronized void removeJob(String jobId) {
        tasks.removeIf(t -> t.jobId().equals(jobId));
        requested.removeIf(t -> t.jobId().equals(jobId));
    }

    /** Next task, waiting at most {@code timeout}; null when none arrived. */
    public Task poll(long timeout, TimeUnit unit) throws InterruptedException {
        Next next = next(timeout, unit);
        return next == null ? null : next.task();
    }

    /** Like {@link #poll} but also says whether the sheet was asked for. */
    public synchronized Next next(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (tasks.isEmpty()) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return null;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        Task task = tasks.remove(0);
        return new Next(task, requested.remove(task));
    }

    public synchronized List<Task> snapshot() {
        return List.copyOf(tasks);
    }

    public synchronized boolean contains(String jobId, int index) {
        return tasks.contains(new Task(jobId, index));
    }
}
