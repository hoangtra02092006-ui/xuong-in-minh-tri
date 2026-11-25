package vn.minhtri.xuongin.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.shared.ApiError;
import vn.minhtri.xuongin.shared.ErrorCode;

class InMemoryJobStoreTest {

    @Test
    void jobLifecycleIsImmutableAndStored() {
        var store = new InMemoryJobStore();
        Job pending = Job.pending("j1", null);
        store.save(pending);
        Job running = pending.running(10);
        store.save(running);
        assertThat(pending.status()).isEqualTo(JobStatus.PENDING);
        assertThat(store.find("j1")).contains(running);
        Job failed = running.failed(ApiError.of(ErrorCode.NESTING_FAILED, "x", java.util.Map.of()).error());
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.finishedAt()).isNotNull();
        assertThat(running.done(null).progress()).isEqualTo(100);
    }

    @Test
    void purgeRemovesOldJobsOnly() {
        var store = new InMemoryJobStore();
        store.save(Job.pending("old", null));
        assertThat(store.purgeOlderThan(Instant.now().minusSeconds(60))).isZero();
        assertThat(store.purgeOlderThan(Instant.now().plusSeconds(1))).isEqualTo(1);
        assertThat(store.find("old")).isEmpty();
    }
}
