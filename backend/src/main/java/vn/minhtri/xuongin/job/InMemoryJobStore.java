package vn.minhtri.xuongin.job;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryJobStore implements JobStore {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(Job job) {
        jobs.put(job.jobId(), job);
    }

    @Override
    public Optional<Job> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        int before = jobs.size();
        jobs.values().removeIf(j -> j.createdAt().isBefore(cutoff));
        return before - jobs.size();
    }
}
