package vn.minhtri.xuongin.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.job.InMemoryJobStore;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.storage.DiskFileStore;
import vn.minhtri.xuongin.storage.FileStore;
import vn.minhtri.xuongin.storage.RetentionJanitorAccess;

/** Disk layout of built exports and their removal with the job (spec 11). */
class ExportStoreTest {

    @TempDir
    Path dir;

    InMemoryJobStore jobs = new InMemoryJobStore();

    private ExportStore store() {
        return new ExportStore(dir, jobs, mock(FileStore.class), mock(PdfComposer.class));
    }

    @Test
    void layoutUnderStorageExports() {
        ExportStore store = store();
        assertThat(store.root()).isEqualTo(dir.toAbsolutePath().resolve("exports"));
        assertThat(store.tifPath("abc", 0)).isEqualTo(store.root().resolve("abc").resolve("tam-01.tif"));
        assertThat(store.sheetFile("abc", 11, "-cat.pdf").getFileName().toString()).isEqualTo("tam-12-cat.pdf");
        assertThatThrownBy(() -> store.jobDir("../x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ensureWritesOnceAndAtomically() throws IOException {
        ExportStore store = store();
        AtomicInteger writes = new AtomicInteger();
        Path target = store.sheetFile("j", 0, ".pdf");
        store.ensure(target, out -> {
            writes.incrementAndGet();
            out.write(new byte[] {1, 2, 3});
        });
        store.ensure(target, out -> writes.incrementAndGet());
        assertThat(writes).hasValue(1);
        assertThat(Files.readAllBytes(target)).containsExactly(1, 2, 3);
        assertThat(target.resolveSibling("tam-01.pdf.tmp")).doesNotExist();
    }

    @Test
    void failedWriteLeavesNoFile() {
        ExportStore store = store();
        Path target = store.sheetFile("j", 0, ".pdf");
        assertThatThrownBy(() -> store.ensure(target, out -> {
            out.write(1);
            throw new IOException("disk full");
        })).hasMessage("disk full");
        assertThat(target).doesNotExist();
        assertThat(target.resolveSibling("tam-01.pdf.tmp")).doesNotExist();
    }

    @Test
    void purgeRemovesFoldersOfGoneOrOldJobsOnly() throws IOException {
        ExportStore store = store();
        List<String> forgotten = new ArrayList<>();
        store.onPurge(forgotten::add);
        Job live = Job.pending("live", null);
        jobs.save(live);
        Files.createDirectories(store.jobDir("live"));
        Files.write(store.tifPath("live", 0), new byte[] {1});
        Files.createDirectories(store.jobDir("gone").resolve("sub"));
        Files.write(store.tifPath("gone", 0), new byte[] {1});

        assertThat(store.purgeOlderThan(Instant.EPOCH)).isEqualTo(1);
        assertThat(store.jobDir("gone")).doesNotExist();
        assertThat(store.tifPath("live", 0)).exists();
        assertThat(forgotten).containsExactly("gone");

        Files.setLastModifiedTime(store.jobDir("live"), FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
        assertThat(store.purgeOlderThan(Instant.parse("2021-01-01T00:00:00Z"))).isEqualTo(1);
        assertThat(store.jobDir("live")).doesNotExist();
    }

    @Test
    void purgeWithoutExportFolderIsNoop() {
        assertThat(store().purgeOlderThan(Instant.now())).isZero();
    }

    @Test
    void retentionSweepRemovesTheExportsOfPurgedJobs() throws IOException {
        ExportStore store = store();
        jobs.save(Job.pending("old", null));
        Files.createDirectories(store.jobDir("old"));
        Files.write(store.tifPath("old", 0), new byte[] {1});

        // Every job is older than "now + 1 h minus 0 h": retention of 1 h with a clock 2 h ahead.
        var result = RetentionJanitorAccess.sweep(new DiskFileStore(dir), jobs, dir, store,
                Instant.now().plusSeconds(2 * 3600), 1);
        assertThat(result.jobsRemoved()).isEqualTo(1);
        assertThat(result.extrasRemoved()).isEqualTo(1);
        assertThat(store.jobDir("old")).doesNotExist();
    }
}
