package vn.minhtri.xuongin.storage;

import java.time.Instant;

/**
 * Extra data another module keeps (for example the built exports of a job) that must be removed
 * with the jobs and files. {@link RetentionJanitor} calls every bean of this type after purging
 * jobs, so a participant can also drop data whose job is gone.
 */
public interface RetentionParticipant {

    /** Removes data older than {@code cutoff} or whose job no longer exists; returns the count removed. */
    int purgeOlderThan(Instant cutoff);
}
