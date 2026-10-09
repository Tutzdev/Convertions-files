package dev.tutz.boostconvert.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Jobs live in memory: they are short-lived (minutes) and their files are on this instance's disk
 * anyway. Running several instances would move both to shared storage (Redis and S3, for example).
 */
@Component
public class JobStore {

    private final ConcurrentMap<UUID, ConversionJob> jobs = new ConcurrentHashMap<>();

    public void add(ConversionJob job) {
        jobs.put(job.id(), job);
    }

    public Optional<ConversionJob> find(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /**
     * Applies a transition atomically. Empty when the job no longer exists, which is how a worker
     * finds out that the user deleted the job while it was queued or running.
     */
    public Optional<ConversionJob> update(UUID id, UnaryOperator<ConversionJob> transition) {
        return Optional.ofNullable(jobs.computeIfPresent(id, (key, job) -> transition.apply(job)));
    }

    public Optional<ConversionJob> remove(UUID id) {
        return Optional.ofNullable(jobs.remove(id));
    }

    public List<ConversionJob> all() {
        return List.copyOf(jobs.values());
    }

    public int size() {
        return jobs.size();
    }
}
