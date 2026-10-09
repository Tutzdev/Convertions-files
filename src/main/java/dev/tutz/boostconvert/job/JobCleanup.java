package dev.tutz.boostconvert.job;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Uploaded files are personal data: nothing is kept longer than the retention period. */
@Component
class JobCleanup {

    private static final Logger log = LoggerFactory.getLogger(JobCleanup.class);

    private final JobStore jobs;
    private final JobFiles files;
    private final JobProperties properties;
    private final Clock clock;

    JobCleanup(JobStore jobs, JobFiles files, JobProperties properties, Clock clock) {
        this.jobs = jobs;
        this.files = files;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${boostconvert.jobs.cleanup-interval}")
    void removeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (ConversionJob job : jobs.all()) {
            if (job.expiresAt(properties.retention()).filter(now::isAfter).isPresent()) {
                jobs.remove(job.id());
                files.delete(job.id());
                removed++;
            }
        }
        // Directories without a job: a delete that raced a running conversion, or a failed cleanup.
        // The age check protects uploads that are being saved right now and have no job record yet.
        for (UUID id : files.storedJobIds()) {
            if (jobs.find(id).isEmpty() && files.lastModified(id).plus(properties.retention()).isBefore(now)) {
                files.delete(id);
            }
        }
        if (removed > 0) {
            log.info("Removed {} expired conversion(s)", removed);
        }
    }
}
