package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.common.FileNames;
import dev.tutz.boostconvert.converter.ConversionFailedException;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import dev.tutz.boostconvert.job.ConversionJob.Failure;
import dev.tutz.boostconvert.job.ConversionJob.FileInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** What a worker thread does with one job. */
@Component
public class ConversionRunner {

    private static final Logger log = LoggerFactory.getLogger(ConversionRunner.class);
    private static final Failure UNEXPECTED = new Failure("CONVERSION_FAILED", "The file could not be converted.");

    private final JobStore jobs;
    private final JobFiles files;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ConversionRunner(JobStore jobs, JobFiles files, ApplicationEventPublisher events, Clock clock) {
        this.jobs = jobs;
        this.files = files;
        this.events = events;
        this.clock = clock;
    }

    public void run(UUID jobId, Converter converter, SourceFile source) {
        ConversionJob job = jobs.update(jobId, queued -> queued.start(clock.instant())).orElse(null);
        if (job == null) {
            // Deleted by the user while it was waiting in the queue.
            files.delete(jobId);
            return;
        }

        long started = System.nanoTime();
        FileInfo output = null;
        Failure failure = UNEXPECTED;
        try {
            output = convert(job, converter, source);
            failure = null;
        } catch (ConversionFailedException rejected) {
            failure = new Failure(rejected.getCode(), rejected.getMessage());
            log.info("Job {} ({}) rejected the file: {}", jobId, converter.info().id(), rejected.getCode());
        } catch (Exception unexpected) {
            log.error("Job {} ({}) failed", jobId, converter.info().id(), unexpected);
        } finally {
            // Also runs when an Error escapes, so a job is never left RUNNING forever.
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            files.deleteInput(jobId);
            finish(jobId, output, failure);
            events.publishEvent(new ConversionCompleted(converter.info().id(), failure == null, elapsed));
        }
    }

    private FileInfo convert(ConversionJob job, Converter converter, SourceFile source) throws IOException {
        FileFormat produced;
        try (OutputStream target = files.openResult(job.id())) {
            produced = converter.convert(source, target);
        }
        String name = FileNames.baseName(job.source().name()) + "." + produced.extension();
        return new FileInfo(name, produced, files.resultSize(job.id()));
    }

    private void finish(UUID jobId, FileInfo output, Failure failure) {
        if (failure != null) {
            // A converter that failed halfway may have written part of a file.
            files.deleteResult(jobId);
        }
        boolean stillExists = jobs.update(jobId, running -> failure == null
                ? running.succeed(output, clock.instant())
                : running.fail(failure, clock.instant())).isPresent();
        if (!stillExists) {
            // Deleted by the user while it was running.
            files.delete(jobId);
        }
    }
}
