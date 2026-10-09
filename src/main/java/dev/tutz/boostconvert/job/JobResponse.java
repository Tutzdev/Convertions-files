package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.job.ConversionJob.Failure;
import dev.tutz.boostconvert.job.ConversionJob.FileInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(UUID id, String converter, JobStatus status, FileResponse source, FileResponse result,
        Failure error, Instant createdAt, Instant startedAt, Instant finishedAt, Instant expiresAt, Long durationMs) {

    static JobResponse from(ConversionJob job, Duration retention) {
        FileResponse result = job.result() == null ? null
                : FileResponse.of(job.result(), "/api/v1/conversions/%s/result".formatted(job.id()));
        return new JobResponse(job.id(), job.converterId(), job.status(), FileResponse.of(job.source(), null), result,
                job.failure(), job.createdAt(), job.startedAt(), job.finishedAt(),
                job.expiresAt(retention).orElse(null), job.duration().map(Duration::toMillis).orElse(null));
    }

    public record FileResponse(String name, String format, String mediaType, long sizeBytes, String downloadUrl) {

        static FileResponse of(FileInfo file, String downloadUrl) {
            return new FileResponse(file.name(), file.format().extension(), file.format().mediaType(), file.sizeBytes(),
                    downloadUrl);
        }
    }
}
