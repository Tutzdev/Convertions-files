package dev.tutz.boostconvert.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkerPoolTest {

    @Test
    void fullQueueRejectsWithRetryAfterInsteadOfGrowing() throws InterruptedException {
        JobProperties properties = new JobProperties(1, 1, Duration.ofMinutes(30), Duration.ofSeconds(7), Path.of("unused"));
        WorkerPool pool = new WorkerPool(properties, new SimpleMeterRegistry());
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        try {
            pool.submit(() -> {
                running.countDown();
                await(release);
            });
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            pool.submit(() -> await(release));

            assertThatThrownBy(() -> pool.submit(() -> { }))
                    .isInstanceOf(QueueFullException.class)
                    .satisfies(full -> assertThat(((QueueFullException) full).getHeaders().getFirst("Retry-After"))
                            .isEqualTo("7"));
            assertThat(pool.busy()).isOne();
            assertThat(pool.waiting()).isOne();
            assertThat(pool.rejected()).isOne();
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
