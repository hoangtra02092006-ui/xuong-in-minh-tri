package vn.minhtri.xuongin.job;

import java.time.Instant;
import vn.minhtri.xuongin.nesting.model.NestRequest;
import vn.minhtri.xuongin.nesting.model.NestResult;
import vn.minhtri.xuongin.shared.ApiError;

/**
 * Immutable job record; every state change creates a new instance (spec 11).
 * {@code request} is kept for export and never returned by the API.
 */
public record Job(
        String jobId,
        JobStatus status,
        int progress,
        NestRequest request,
        NestResult result,
        ApiError.Body error,
        Instant createdAt,
        Instant finishedAt) {

    public static Job pending(String jobId, NestRequest request) {
        return new Job(jobId, JobStatus.PENDING, 0, request, null, null, Instant.now(), null);
    }

    public Job running(int progress) {
        return new Job(jobId, JobStatus.RUNNING, progress, request, null, null, createdAt, null);
    }

    public Job done(NestResult result) {
        return new Job(jobId, JobStatus.DONE, 100, request, result, null, createdAt, Instant.now());
    }

    public Job failed(ApiError.Body error) {
        return new Job(jobId, JobStatus.FAILED, progress, request, null, error, createdAt, Instant.now());
    }
}
