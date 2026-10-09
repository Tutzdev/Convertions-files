package dev.tutz.boostconvert.job;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param workers       conversions running at the same time; they are CPU bound, so about one per core
 * @param queueCapacity jobs allowed to wait for a worker before uploads get {@code 429}
 * @param retention     how long a finished job and its files are kept
 * @param retryAfter    suggested wait sent in {@code Retry-After} when the queue is full
 * @param storageDir    where uploads and results live while a job exists
 */
@ConfigurationProperties("boostconvert.jobs")
public record JobProperties(int workers, int queueCapacity, Duration retention, Duration retryAfter, Path storageDir) {
}
