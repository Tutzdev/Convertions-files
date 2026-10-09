package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.common.ApiException;
import dev.tutz.boostconvert.common.FileNames;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterRegistry;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.converter.UnsupportedConversionException;
import dev.tutz.boostconvert.format.DetectedFormat;
import dev.tutz.boostconvert.format.FileFormat;
import dev.tutz.boostconvert.format.FormatDetector;
import dev.tutz.boostconvert.job.ConversionJob.FileInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accepts an upload, decides synchronously whether it can be converted (so the client gets a 4xx
 * right away instead of a job that is doomed to fail), and queues the actual work.
 */
@Service
public class ConversionService {

    private final ConverterRegistry converters;
    private final FormatDetector detector;
    private final JobStore jobs;
    private final JobFiles files;
    private final WorkerPool workers;
    private final ConversionRunner runner;
    private final Clock clock;

    public ConversionService(ConverterRegistry converters, FormatDetector detector, JobStore jobs, JobFiles files,
            WorkerPool workers, ConversionRunner runner, Clock clock) {
        this.converters = converters;
        this.detector = detector;
        this.jobs = jobs;
        this.files = files;
        this.workers = workers;
        this.runner = runner;
        this.clock = clock;
    }

    public ConversionJob submit(MultipartFile upload, String targetFormat) {
        if (upload.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The uploaded file is empty.");
        }
        FileFormat target = FileFormat.fromExtension(targetFormat).orElseThrow(
                () -> new UnsupportedConversionException("Unknown target format '%s'.".formatted(targetFormat)));

        UUID id = UUID.randomUUID();
        String name = FileNames.displayName(upload.getOriginalFilename());
        try {
            Path input = files.saveInput(id, upload);
            DetectedFormat detected = detector.detect(input, name);
            Converter converter = converters.resolve(detected.format(), target);

            ConversionJob job = ConversionJob.queued(id, converter.info().id(),
                    new FileInfo(name, detected.format(), upload.getSize()), clock.instant());
            jobs.add(job);
            SourceFile source = new SourceFile(input, detected.format(), detected.charset());
            try {
                workers.submit(() -> runner.run(id, converter, source));
            } catch (QueueFullException full) {
                jobs.remove(id);
                throw full;
            }
            return job;
        } catch (IOException e) {
            files.delete(id);
            throw new UncheckedIOException(e);
        } catch (RuntimeException rejected) {
            files.delete(id);
            throw rejected;
        }
    }

    public ConversionJob get(UUID id) {
        return jobs.find(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    /** The finished file, or 409 while the job is still running or when it failed. */
    public StoredResult result(UUID id) {
        ConversionJob job = get(id);
        switch (job.status()) {
            case SUCCEEDED -> {
                return new StoredResult(files.result(id), job.result());
            }
            case FAILED -> throw new ApiException(HttpStatus.CONFLICT, "RESULT_NOT_AVAILABLE",
                    "The conversion failed: " + job.failure().message());
            default -> throw new ApiException(HttpStatus.CONFLICT, "RESULT_NOT_READY",
                    "The conversion has not finished yet.");
        }
    }

    /** Removes the job and its files now instead of waiting for the retention period. */
    public void delete(UUID id) {
        jobs.remove(id).orElseThrow(() -> new JobNotFoundException(id));
        files.delete(id);
    }

    public record StoredResult(Path path, FileInfo file) {
    }
}
