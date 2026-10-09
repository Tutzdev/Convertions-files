package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.format.FileFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One conversion request. Immutable: every transition returns a new value, and {@link JobStore}
 * swaps it atomically, so the HTTP threads reading a job never see one half-updated by a worker.
 *
 * <pre>
 * QUEUED -> RUNNING -> SUCCEEDED
 *                   -> FAILED
 * </pre>
 */
public record ConversionJob(UUID id, String converterId, JobStatus status, FileInfo source, FileInfo result,
        Failure failure, Instant createdAt, Instant startedAt, Instant finishedAt) {

    public static ConversionJob queued(UUID id, String converterId, FileInfo source, Instant now) {
        return new ConversionJob(id, converterId, JobStatus.QUEUED, source, null, null, now, null, null);
    }

    public ConversionJob start(Instant now) {
        requireStatus(JobStatus.QUEUED);
        return new ConversionJob(id, converterId, JobStatus.RUNNING, source, null, null, createdAt, now, null);
    }

    public ConversionJob succeed(FileInfo output, Instant now) {
        requireStatus(JobStatus.RUNNING);
        return new ConversionJob(id, converterId, JobStatus.SUCCEEDED, source, output, null, createdAt, startedAt, now);
    }

    public ConversionJob fail(Failure reason, Instant now) {
        requireStatus(JobStatus.RUNNING);
        return new ConversionJob(id, converterId, JobStatus.FAILED, source, null, reason, createdAt, startedAt, now);
    }

    public boolean isFinished() {
        return status == JobStatus.SUCCEEDED || status == JobStatus.FAILED;
    }

    /** Finished jobs are kept for {@code retention}, then their record and files are deleted. */
    public Optional<Instant> expiresAt(Duration retention) {
        return Optional.ofNullable(finishedAt).map(finished -> finished.plus(retention));
    }

    public Optional<Duration> duration() {
        if (startedAt == null || finishedAt == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(startedAt, finishedAt));
    }

    private void requireStatus(JobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Job %s is %s, expected %s".formatted(id, status, expected));
        }
    }

    public record FileInfo(String name, FileFormat format, long sizeBytes) {
    }

    /** Why a job failed. The message is safe to show to the user. */
    public record Failure(String code, String message) {
    }
}
