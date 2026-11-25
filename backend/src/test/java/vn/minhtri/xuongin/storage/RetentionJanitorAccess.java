package vn.minhtri.xuongin.storage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import vn.minhtri.xuongin.job.JobStore;

/** Lets tests in other packages run one sweep with a fixed clock and extra participants. */
public final class RetentionJanitorAccess {

    private RetentionJanitorAccess() {}

    public static RetentionJanitor.SweepResult sweep(FileStore files, JobStore jobs, Path root,
            RetentionParticipant participant, Instant now, int hours) {
        return new RetentionJanitor(files, jobs, hours, root, Clock.fixed(now, ZoneOffset.UTC), 0, List.of(participant))
                .sweep();
    }
}
