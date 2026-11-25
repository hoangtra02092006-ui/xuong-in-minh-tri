package vn.minhtri.xuongin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.annotation.Scheduled;
import vn.minhtri.xuongin.file.ContentBox;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.job.InMemoryJobStore;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.job.JobStatus;

class RetentionJanitorTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path root;

    private final InMemoryJobStore jobs = new InMemoryJobStore();

    @Test
    void sweepPurgesOldFilesOnDiskAndInTheMapAndOldJobs() throws IOException {
        DiskFileStore files = new DiskFileStore(root);
        StoredFile old = put(files, "old", NOW.minusSeconds(49 * 3600));
        put(files, "fresh", NOW.minusSeconds(3600));
        files.savePreview("old", new byte[] {1});
        jobs.save(job("j-old", NOW.minusSeconds(49 * 3600)));
        jobs.save(job("j-new", NOW.minusSeconds(60)));

        RetentionJanitor janitor = new RetentionJanitor(files, jobs, 48, root, CLOCK, 0);
        RetentionJanitor.SweepResult result = janitor.sweep();

        assertThat(result.filesRemoved()).isEqualTo(1);
        assertThat(result.jobsRemoved()).isEqualTo(1);
        assertThat(result.lowDisk()).isFalse();
        assertThat(Files.exists(files.pathOf(old))).isFalse();
        assertThat(files.find("old")).isEmpty();
        assertThat(files.findPreview("old")).isEmpty();
        assertThat(files.find("fresh")).isPresent();
        assertThat(jobs.find("j-old")).isEmpty();
        assertThat(jobs.find("j-new")).isPresent();
    }

    @Test
    void zeroHoursDisablesCleanup() throws IOException {
        DiskFileStore files = new DiskFileStore(root);
        put(files, "old", NOW.minusSeconds(1000 * 3600));
        jobs.save(job("j-old", NOW.minusSeconds(1000 * 3600)));

        RetentionJanitor.SweepResult result = new RetentionJanitor(files, jobs, 0, root, CLOCK, 0).sweep();

        assertThat(result.filesRemoved()).isZero();
        assertThat(files.find("old")).isPresent();
        assertThat(jobs.find("j-old")).isPresent();
    }

    @Test
    void lowFreeDiskIsReported() {
        RetentionJanitor janitor = new RetentionJanitor(new DiskFileStore(root), jobs, 48, root, CLOCK, Long.MAX_VALUE);

        assertThat(janitor.sweep().lowDisk()).isTrue();
    }

    @Test
    void freeSpaceOfAMissingFolderIsReadFromItsParent() {
        assertThat(RetentionJanitor.usableBytes(root.resolve("not").resolve("yet"))).isPositive();
    }

    @Test
    void sweepIsScheduledFromTheRetentionSetting() throws NoSuchMethodException {
        Scheduled scheduled = RetentionJanitor.class.getMethod("scheduledSweep").getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${app.retention.sweep-minutes:60}");
        assertThat(scheduled.timeUnit()).isEqualTo(java.util.concurrent.TimeUnit.MINUTES);
    }

    @Test
    void scheduledEntryPointRuns() {
        DiskFileStore files = new DiskFileStore(root);
        new RetentionJanitor(files, jobs, 48, root, CLOCK, 0).scheduledSweep();
        assertThat(files.find("x")).isEmpty();
    }

    private static Job job(String id, Instant createdAt) {
        return new Job(id, JobStatus.PENDING, 0, null, null, null, createdAt, null);
    }

    private static StoredFile put(DiskFileStore store, String id, Instant uploadedAt) throws IOException {
        String name = store.writeContent(id, ".pdf", new ByteArrayInputStream(new byte[] {1}));
        StoredFile file = new StoredFile(id, id, name, FileType.PDF, 1, 1, 1, 1,
                new ContentBox(0, 0, 1, 1), null, 0, 1, true, 1, uploadedAt);
        store.save(file);
        return file;
    }
}
