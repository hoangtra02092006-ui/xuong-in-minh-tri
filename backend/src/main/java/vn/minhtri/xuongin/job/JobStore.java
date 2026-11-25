package vn.minhtri.xuongin.job;

import java.time.Instant;
import java.util.Optional;

/** Job storage. In-memory today; interface so it can move to a database. */
public interface JobStore {

    void save(Job job);

    Optional<Job> find(String jobId);

    /** Removes jobs created before {@code cutoff}. Returns count removed. */
    int purgeOlderThan(Instant cutoff);
}
