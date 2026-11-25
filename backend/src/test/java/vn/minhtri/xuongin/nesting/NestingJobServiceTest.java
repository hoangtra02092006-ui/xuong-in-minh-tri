package vn.minhtri.xuongin.nesting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.file.FileType;
import vn.minhtri.xuongin.file.StoredFile;
import vn.minhtri.xuongin.job.InMemoryJobStore;
import vn.minhtri.xuongin.job.Job;
import vn.minhtri.xuongin.job.JobStatus;
import vn.minhtri.xuongin.job.JobStore;
import vn.minhtri.xuongin.nesting.model.NestItemRequest;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.nesting.model.NestStats;
import vn.minhtri.xuongin.nesting.model.Sheet;
import vn.minhtri.xuongin.shared.ApiException;
import vn.minhtri.xuongin.shared.ErrorCode;
import vn.minhtri.xuongin.storage.DiskFileStore;

/** Job lifecycle (spec 11) with a fake planner, run synchronously. */
class NestingJobServiceTest {

    static final NestRequest REQUEST = new NestRequest(null, 570, 5, 3, null, true, false,
            List.of(new NestItemRequest("a", 1, true, null, null)));
    static final NestResult RESULT = new NestResult(List.of(new Sheet(0, 570, 100, 0.5, List.of())),
            new NestStats(1, 100, 1, 1, 0.5, 0, 1, List.of()));

    /** Records every saved version so the progress sequence can be checked. */
    static class RecordingStore extends InMemoryJobStore {
        final List<Job> saved = new ArrayList<>();

        @Override
        public synchronized void save(Job job) {
            saved.add(job);
            super.save(job);
        }
    }

    final RecordingStore jobs = new RecordingStore();
    final List<Object> events = new ArrayList<>();
    NestingJobService service;

    private NestingJobService service(NestingPlanner planner) {
        service = new NestingJobService(jobs, new DiskFileStore(java.nio.file.Path.of("target", "unused")), planner,
                events::add, NestingProperties.defaults());
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    private static Map<String, StoredFile> files() {
        return Map.of("a", NestRequestValidatorTest.file("a", FileType.PDF, 10, 10, null, 0));
    }

    @Test
    void runGoesPendingRunningDoneWithRealProgress() {
        NestingJobService s = service((req, files, progress) -> {
            progress.accept(0);
            progress.accept(50);
            progress.accept(50);
            progress.accept(100);
            return RESULT;
        });
        Job pending = Job.pending("j", REQUEST);
        s.run(pending, files());

        assertThat(jobs.saved).extracting(Job::status)
                .containsExactly(JobStatus.RUNNING, JobStatus.RUNNING, JobStatus.RUNNING, JobStatus.DONE);
        assertThat(jobs.saved).extracting(Job::progress).containsExactly(10, 54, 99, 100);
        Job done = s.require("j");
        assertThat(done.result()).isSameAs(RESULT);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(events).containsExactly(new JobFinishedEvent(done));
    }

    @Test
    void engineErrorEndsFailedWithItsCode() {
        NestingJobService s = service((req, files, progress) -> {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Lề biên quá lớn so với khổ ngang.");
        });
        s.run(Job.pending("j", REQUEST), files());
        Job failed = s.require("j");
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.error().code()).isEqualTo("INVALID_REQUEST");
        assertThat(failed.error().message()).isEqualTo("Lề biên quá lớn so với khổ ngang.");
        assertThat(events).hasSize(1);
    }

    @Test
    void unexpectedErrorEndsNestingFailed() {
        NestingJobService s = service((req, files, progress) -> {
            throw new IllegalStateException("tràn");
        });
        s.run(Job.pending("j", REQUEST), files());
        assertThat(s.require("j").error().code()).isEqualTo("NESTING_FAILED");
        assertThat(s.require("j").error().message()).isEqualTo("Thuật toán gặp lỗi không mong muốn: tràn");
    }

    @Test
    void lookupsAnswerSpecErrors() {
        NestingJobService s = service((req, files, progress) -> RESULT);
        assertThatThrownBy(() -> s.require("x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.JOB_NOT_FOUND));
        jobs.save(Job.pending("p", REQUEST));
        assertThatThrownBy(() -> s.requireDone("p"))
                .hasMessage("Lần ghép này chưa có kết quả (trạng thái: đang chờ).");
        jobs.save(Job.pending("r", REQUEST).running(10));
        assertThatThrownBy(() -> s.requireDone("r"))
                .hasMessage("Lần ghép này chưa có kết quả (trạng thái: đang ghép).");
        Job done = Job.pending("d", REQUEST).done(RESULT);
        jobs.save(done);
        assertThat(s.requireDone("d")).isSameAs(done);
        assertThat(NestingJobService.requireSheet(done, 0)).isSameAs(RESULT.sheets().get(0));
        assertThatThrownBy(() -> NestingJobService.requireSheet(done, -1))
                .hasMessage("Lần ghép này chỉ có 1 tấm, không có tấm số 0.");
    }

    @Test
    void createValidatesBeforeStoringAnything() {
        JobStore store = jobs;
        NestingJobService s = service((req, files, progress) -> RESULT);
        assertThatThrownBy(() -> s.create(REQUEST))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FILE_NOT_FOUND));
        assertThat(store.find("anything")).isEmpty();
        assertThat(jobs.saved).isEmpty();
    }

    @Test
    void plannerPercentIsClampedToRealRange() {
        assertThat(EngineNestingPlanner.percent(5, 10)).isEqualTo(50);
        assertThat(EngineNestingPlanner.percent(5, 0)).isZero();
        assertThat(EngineNestingPlanner.percent(20, 10)).isEqualTo(100);
    }
}
