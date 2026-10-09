package dev.tutz.boostconvert.stats;

import dev.tutz.boostconvert.job.ConversionCompleted;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Counts finished conversions per converter for the dashboard, and records the same events as a
 * Micrometer timer ({@code boostconvert.conversions{converter, outcome}}) for Prometheus.
 */
@Component
public class ConversionStats {

    private final MeterRegistry meters;
    private final ConcurrentMap<String, Counters> byConverter = new ConcurrentHashMap<>();

    public ConversionStats(MeterRegistry meters) {
        this.meters = meters;
    }

    @EventListener
    void on(ConversionCompleted completed) {
        Counters counters = byConverter.computeIfAbsent(completed.converterId(), id -> new Counters());
        (completed.succeeded() ? counters.succeeded : counters.failed).increment();
        counters.totalMillis.add(completed.duration().toMillis());

        Timer.builder("boostconvert.conversions")
                .description("Time spent converting files")
                .tag("converter", completed.converterId())
                .tag("outcome", completed.succeeded() ? "succeeded" : "failed")
                .register(meters)
                .record(completed.duration());
    }

    public ConverterStats of(String converterId) {
        Counters counters = byConverter.get(converterId);
        if (counters == null) {
            return ConverterStats.NONE;
        }
        return new ConverterStats(counters.succeeded.sum(), counters.failed.sum(), counters.totalMillis.sum());
    }

    public record ConverterStats(long succeeded, long failed, long totalMillis) {

        public static final ConverterStats NONE = new ConverterStats(0, 0, 0);

        public ConverterStats plus(ConverterStats other) {
            return new ConverterStats(succeeded + other.succeeded, failed + other.failed, totalMillis + other.totalMillis);
        }

        public long finished() {
            return succeeded + failed;
        }

        public long averageMs() {
            return finished() == 0 ? 0 : totalMillis / finished();
        }
    }

    private static final class Counters {
        private final LongAdder succeeded = new LongAdder();
        private final LongAdder failed = new LongAdder();
        private final LongAdder totalMillis = new LongAdder();
    }
}
