package dev.tutz.boostconvert.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tutz.boostconvert.format.FileFormat;
import dev.tutz.boostconvert.job.ConversionJob.Failure;
import dev.tutz.boostconvert.job.ConversionJob.FileInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversionJobTest {

    private static final Instant T0 = Instant.parse("2026-10-09T12:00:00Z");
    private static final FileInfo SOURCE = new FileInfo("photo.png", FileFormat.PNG, 1_000);
    private static final FileInfo RESULT = new FileInfo("photo.jpg", FileFormat.JPG, 400);

    @Test
    void happyPathRecordsTimesAndResult() {
        ConversionJob done = ConversionJob.queued(UUID.randomUUID(), "png-to-jpg", SOURCE, T0)
                .start(T0.plusSeconds(1))
                .succeed(RESULT, T0.plusMillis(1_250));

        assertThat(done.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(done.result()).isEqualTo(RESULT);
        assertThat(done.duration()).contains(Duration.ofMillis(250));
        assertThat(done.expiresAt(Duration.ofMinutes(30))).contains(T0.plusMillis(1_250).plus(Duration.ofMinutes(30)));
    }

    @Test
    void unfinishedJobsNeverExpire() {
        ConversionJob running = ConversionJob.queued(UUID.randomUUID(), "png-to-jpg", SOURCE, T0).start(T0);

        assertThat(running.isFinished()).isFalse();
        assertThat(running.expiresAt(Duration.ofMinutes(30))).isEmpty();
    }

    @Test
    void transitionsOnlyMoveForward() {
        ConversionJob queued = ConversionJob.queued(UUID.randomUUID(), "png-to-jpg", SOURCE, T0);
        ConversionJob failed = queued.start(T0).fail(new Failure("CORRUPT_FILE", "broken"), T0);

        assertThatThrownBy(() -> queued.succeed(RESULT, T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.start(T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.succeed(RESULT, T0)).isInstanceOf(IllegalStateException.class);
    }
}
