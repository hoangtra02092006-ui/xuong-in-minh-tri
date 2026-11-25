package vn.minhtri.xuongin.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import vn.minhtri.xuongin.export.pdf.PdfComposer;
import vn.minhtri.xuongin.export.tif.TifResult;
import vn.minhtri.xuongin.job.InMemoryJobStore;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.nesting.JobFinishedEvent;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiError;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.storage.FileStore;

/** Background TIF queue (design 4a) with a fake builder: order, prioritize, failure, retry, wait, purge. */
class TifPrebuildServiceTest {

    @TempDir
    Path dir;

    InMemoryJobStore jobs;
    ExportStore store;
    SheetTifBuilder builder;
    TifPrebuildService service;

    @BeforeEach
    void setUp() throws IOException {
        jobs = new InMemoryJobStore();
        store = new ExportStore(dir, jobs, mock(FileStore.class), mock(PdfComposer.class));
        builder = mock(SheetTifBuilder.class);
        when(builder.build(any(), any(), any())).thenAnswer(inv -> {
            Path target = inv.getArgument(2);
            Files.createDirectories(target.getParent());
            Files.write(target, new byte[123]);
            return new TifResult(target, 123, 1, 1, 300);
        });
        service = new TifPrebuildService(jobs, store, builder);
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    static Job doneJob(String id, int sheets) {
        List<Sheet> list = new ArrayList<>();
        for (int i = 0; i < sheets; i++) {
            list.add(new Sheet(i, 100, 50, 0.5, List.of()));
        }
        return Job.pending(id, null).done(new NestResult(list, null));
    }

    private Job register(String id, int sheets) {
        Job job = doneJob(id, sheets);
        jobs.save(job);
        service.onJobFinished(new JobFinishedEvent(job));
        return job;
    }

    private static Sheet sheet(int index) {
        return argThat((Sheet s) -> s != null && s.index() == index);
    }

    @Test
    void finishedJobIsQueuedAndBuiltOneSheetAtATimeInOrder() throws Exception {
        register("j", 2);
        assertThat(service.statuses("j").orElseThrow()).containsOnly(SheetTif.QUEUED);

        assertThat(service.runOnce(0)).isTrue();
        assertThat(service.statuses("j").orElseThrow()).containsExactly(SheetTif.ready(123), SheetTif.QUEUED);
        assertThat(service.runOnce(0)).isTrue();
        assertThat(service.runOnce(0)).isFalse();

        InOrder order = inOrder(builder);
        order.verify(builder).build(any(), sheet(0), any());
        order.verify(builder).build(any(), sheet(1), any());
        assertThat(dir.resolve("exports/j/tam-01.tif")).exists();
    }

    @Test
    void failedJobsAreNotQueued() {
        Job failed = Job.pending("f", null).failed(new ApiError.Body("INVALID_REQUEST", "x", java.util.Map.of()));
        service.onJobFinished(new JobFinishedEvent(failed));
        assertThat(service.statuses("f")).isEmpty();
    }

    @Test
    void prioritizeBuildsThatSheetNext() throws Exception {
        Job job = register("j", 3);
        assertThat(service.prioritize(job, 2)).isEqualTo(SheetTif.QUEUED);
        assertThat(service.queue().snapshot().get(0)).isEqualTo(new TifQueue.Task("j", 2));
        service.runOnce(0);
        assertThat(service.statuses("j").orElseThrow().get(2).status()).isEqualTo(SheetTif.Status.READY);
        assertThat(service.statuses("j").orElseThrow().get(0).status()).isEqualTo(SheetTif.Status.QUEUED);
    }

    @Test
    void failureIsRecordedWithItsCodeAndRetriedWhenAskedAgain() throws Exception {
        Job job = register("j", 1);
        doThrow(new ApiException(ErrorCode.TIFF_TOO_LARGE, "Tấm 1 quá lớn.")).when(builder).build(any(), any(), any());
        service.runOnce(0);
        SheetTif failed = service.statuses("j").orElseThrow().get(0);
        assertThat(failed.status()).isEqualTo(SheetTif.Status.FAILED);
        assertThat(failed.error().code()).isEqualTo("TIFF_TOO_LARGE");
        assertThat(failed.error().message()).isEqualTo("Tấm 1 quá lớn.");
        assertThatThrownBy(() -> service.await(job, 0, 10))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.JOB_NOT_READY));

        // await re-queued it; the next build succeeds.
        assertThat(service.statuses("j").orElseThrow().get(0)).isEqualTo(SheetTif.QUEUED);
        org.mockito.Mockito.reset(builder);
        when(builder.build(any(), any(), any())).thenReturn(new TifResult(dir.resolve("x.tif"), 5, 1, 1, 300));
        service.runOnce(0);
        assertThat(service.statuses("j").orElseThrow().get(0)).isEqualTo(SheetTif.ready(5));
    }

    @Test
    void unexpectedErrorBecomesInternalErrorInVietnamese() throws Exception {
        register("j", 1);
        doThrow(new IllegalStateException("boom")).when(builder).build(any(), any(), any());
        service.runOnce(0);
        SheetTif failed = service.statuses("j").orElseThrow().get(0);
        assertThat(failed.error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(failed.error().message()).isEqualTo("Không dựng được file TIF tấm 1: boom");
    }

    @Test
    void awaitWaitsForTheWorkerAndReturnsTheFile() {
        Job job = register("j", 3);
        service.start();
        Path tif = service.await(job, 2, 10_000);
        assertThat(tif).isEqualTo(dir.resolve("exports/j/tam-03.tif").toAbsolutePath());
        assertThat(service.await(job, 2, 10_000)).isEqualTo(tif);
    }

    @Test
    void awaitRethrowsTheBuildError() {
        Job job = register("j", 1);
        try {
            doThrow(new ApiException(ErrorCode.TIFF_TOO_LARGE, "lớn quá")).when(builder).build(any(), any(), any());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        service.start();
        assertThatThrownBy(() -> service.await(job, 0, 10_000))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.TIFF_TOO_LARGE);
                    assertThat(e.getMessage()).isEqualTo("lớn quá");
                });
    }

    @Test
    void readySheetWhoseFileVanishedIsBuiltAgain() throws Exception {
        Job job = register("j", 1);
        service.runOnce(0);
        Files.delete(dir.resolve("exports/j/tam-01.tif"));
        assertThat(service.prioritize(job, 0)).isEqualTo(SheetTif.QUEUED);
        service.runOnce(0);
        assertThat(service.statuses("j").orElseThrow().get(0).status()).isEqualTo(SheetTif.Status.READY);
    }

    @Test
    void jobGoneFromTheStoreFailsTheSheet() throws Exception {
        service.register(doneJob("ghost", 1));
        service.runOnce(0);
        assertThat(service.statuses("ghost").orElseThrow().get(0).error().code()).isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    void purgeOfTheExportFolderForgetsTheJob() throws Exception {
        register("j", 2);
        service.runOnce(0);
        jobs.purgeOlderThan(Instant.now().plusSeconds(1));
        assertThat(store.purgeOlderThan(Instant.EPOCH)).isEqualTo(1);
        assertThat(service.statuses("j")).isEmpty();
        assertThat(service.queue().snapshot()).isEmpty();
        assertThat(dir.resolve("exports/j")).doesNotExist();
    }
}
