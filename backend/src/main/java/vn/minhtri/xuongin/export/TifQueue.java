package vn.minhtri.xuongin.export;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Order in which sheets get their TIF built (design 4a): jobs in the order they finished, sheets
 * from 1 up; {@link #prioritize} moves one sheet to the front. Thread-safe; one consumer.
 */
public final class TifQueue {

    /** One sheet of one job. */
    public record Task(String jobId, int index) {}

    private final Deque<Task> tasks = new ArrayDeque<>();

    /** Appends sheets {@code 0..count-1} of a job. */
    public synchronized void enqueueJob(String jobId, int count) {
        for (int i = 0; i < count; i++) {
            Task task = new Task(jobId, i);
            if (!tasks.contains(task)) {
                tasks.addLast(task);
            }
        }
        notifyAll();
    }

    /** Puts the sheet at the front, whether it was waiting further back or not queued at all. */
    public synchronized void prioritize(String jobId, int index) {
        Task task = new Task(jobId, index);
        tasks.remove(task);
        tasks.addFirst(task);
        notifyAll();
    }

    /** Drops every waiting sheet of a job (job purged). */
    public synchronized void removeJob(String jobId) {
        tasks.removeIf(t -> t.jobId().equals(jobId));
    }

    /** Next task, waiting at most {@code timeout}; null when none arrived. */
    public synchronized Task poll(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (tasks.isEmpty()) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return null;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return tasks.pollFirst();
    }

    public synchronized List<Task> snapshot() {
        return List.copyOf(tasks);
    }

    public synchronized boolean contains(String jobId, int index) {
        return tasks.contains(new Task(jobId, index));
    }
}
