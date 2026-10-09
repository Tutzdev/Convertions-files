package dev.tutz.boostconvert.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fixed workers and a bounded queue. Conversions are CPU and memory heavy, so accepting work without
 * a limit would trade a clear {@code 429} for an out-of-memory crash that loses every job. HTTP
 * requests run on virtual threads; conversions never do, because they would not yield anyway.
 */
@Component
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(30);

    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final Duration retryAfter;
    private final AtomicLong rejected = new AtomicLong();

    public WorkerPool(JobProperties properties, MeterRegistry meters) {
        this.queueCapacity = properties.queueCapacity();
        this.retryAfter = properties.retryAfter();
        this.executor = new ThreadPoolExecutor(properties.workers(), properties.workers(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                Thread.ofPlatform().name("conversion-", 1).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        new ExecutorServiceMetrics(executor, "conversions", List.of()).bindTo(meters);
    }

    public void submit(Runnable conversion) {
        try {
            executor.execute(conversion);
        } catch (RejectedExecutionException full) {
            rejected.incrementAndGet();
            throw new QueueFullException(retryAfter);
        }
    }

    public int workers() {
        return executor.getCorePoolSize();
    }

    public int busy() {
        return executor.getActiveCount();
    }

    public int waiting() {
        return executor.getQueue().size();
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public long rejected() {
        return rejected.get();
    }

    /** Lets running conversions finish on shutdown; queued ones are dropped with their in-memory jobs. */
    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_GRACE.toSeconds(), TimeUnit.SECONDS)) {
            log.warn("Conversions still running after {}; interrupting them", SHUTDOWN_GRACE);
            executor.shutdownNow();
        }
    }
}
