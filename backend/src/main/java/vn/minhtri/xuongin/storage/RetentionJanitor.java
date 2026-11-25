package vn.minhtri.xuongin.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.minhtri.xuongin.job.JobStore;
import vn.minhtri.xuongin.shared.AppProperties;

/**
 * Deletes jobs and uploaded files (disk, metadata, previews) older than {@code app.retention.hours}
 * every {@code app.retention.sweep-minutes}. {@code hours = 0} disables cleanup.
 * Also warns when the disk holding the storage folder runs low (server disk is shared with n8n).
 */
@Component
public class RetentionJanitor {

    /** Below this much free space the log gets a warning (design section 7: disk had 12 GB left). */
    static final long LOW_DISK_BYTES = 3L * 1024 * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(RetentionJanitor.class);

    private final FileStore files;
    private final JobStore jobs;
    private final int retentionHours;
    private final Path storageRoot;
    private final Clock clock;
    private final long lowDiskBytes;

    @Autowired
    public RetentionJanitor(FileStore files, JobStore jobs, AppProperties props) {
        this(files, jobs, props.retention().hours(), Path.of(props.storage().path()), Clock.systemUTC(), LOW_DISK_BYTES);
    }

    RetentionJanitor(FileStore files, JobStore jobs, int retentionHours, Path storageRoot, Clock clock, long lowDiskBytes) {
        this.files = files;
        this.jobs = jobs;
        this.retentionHours = retentionHours;
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.clock = clock;
        this.lowDiskBytes = lowDiskBytes;
    }

    /** Result of one sweep, for logs and tests. */
    public record SweepResult(int filesRemoved, int jobsRemoved, boolean lowDisk) {}

    @Scheduled(
            fixedDelayString = "${app.retention.sweep-minutes:60}",
            initialDelayString = "${app.retention.sweep-minutes:60}",
            timeUnit = TimeUnit.MINUTES)
    public void scheduledSweep() {
        sweep();
    }

    public SweepResult sweep() {
        int filesRemoved = 0;
        int jobsRemoved = 0;
        if (retentionHours > 0) {
            Instant cutoff = clock.instant().minus(Duration.ofHours(retentionHours));
            filesRemoved = files.purgeOlderThan(cutoff);
            jobsRemoved = jobs.purgeOlderThan(cutoff);
            if (filesRemoved > 0 || jobsRemoved > 0) {
                log.info("Retention sweep removed {} files and {} jobs older than {}", filesRemoved, jobsRemoved, cutoff);
            }
        }
        boolean lowDisk = checkFreeDisk();
        return new SweepResult(filesRemoved, jobsRemoved, lowDisk);
    }

    private boolean checkFreeDisk() {
        Long free = usableBytes(storageRoot);
        if (free != null && free < lowDiskBytes) {
            log.warn("Low disk space: {} MB free for the storage folder (warning below {} MB)",
                    free / (1024 * 1024), lowDiskBytes / (1024 * 1024));
            return true;
        }
        return false;
    }

    /** Free bytes on the volume holding {@code path} (or its nearest existing parent); null when unknown. */
    static Long usableBytes(Path path) {
        Path existing = path;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return null;
        }
        try {
            return Files.getFileStore(existing).getUsableSpace();
        } catch (IOException e) {
            log.warn("Could not read free disk space for {}", existing, e);
            return null;
        }
    }
}
