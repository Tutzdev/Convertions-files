package dev.tutz.boostconvert.job;

import java.time.Duration;

/** Published when a worker finishes a conversion, successfully or not. */
public record ConversionCompleted(String converterId, boolean succeeded, Duration duration) {
}
