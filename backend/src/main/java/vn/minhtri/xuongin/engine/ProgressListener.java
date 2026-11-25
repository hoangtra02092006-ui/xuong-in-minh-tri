package vn.minhtri.xuongin.engine;

/**
 * Receives real progress: {@code tried} combinations out of an estimated {@code total}.
 * Called on the engine thread; the last call is always (total, total).
 */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (tried, total) -> {};

    void onProgress(long tried, long total);
}
