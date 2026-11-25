package vn.minhtri.xuongin.engine;

/** Counts tried combinations against an estimated total; never reports 100 % before {@link #finish()}. */
final class Progress {

    private final ProgressListener listener;
    private long total;
    private long tried;

    Progress(ProgressListener listener, long total) {
        this.listener = listener == null ? ProgressListener.NONE : listener;
        this.total = Math.max(1, total);
    }

    void step() {
        tried++;
        if (tried >= total) {
            // The estimate was short (e.g. more sheets than expected): stretch it instead of overshooting.
            total = tried + Math.max(1, tried / 10);
        }
        listener.onProgress(tried, total);
    }

    void finish() {
        listener.onProgress(total, total);
    }
}
