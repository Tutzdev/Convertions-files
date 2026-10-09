package dev.tutz.boostconvert.stats;

import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.ConverterRegistry;
import dev.tutz.boostconvert.job.JobProperties;
import dev.tutz.boostconvert.job.JobStore;
import dev.tutz.boostconvert.job.WorkerPool;
import dev.tutz.boostconvert.stats.ConversionStats.ConverterStats;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aggregates only: no file names and no job ids, so it is safe to show to anyone. */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final ConverterRegistry registry;
    private final ConversionStats stats;
    private final WorkerPool workers;
    private final JobStore jobs;
    private final JobProperties properties;

    public StatsController(ConverterRegistry registry, ConversionStats stats, WorkerPool workers, JobStore jobs,
            JobProperties properties) {
        this.registry = registry;
        this.stats = stats;
        this.workers = workers;
        this.jobs = jobs;
        this.properties = properties;
    }

    @GetMapping
    StatsResponse stats() {
        List<ConverterInfo> catalog = registry.catalog();
        ConverterStats all = catalog.stream()
                .map(info -> stats.of(info.id()))
                .reduce(ConverterStats.NONE, ConverterStats::plus);

        return new StatsResponse(
                new Totals(all.succeeded(), all.failed(), workers.rejected(), all.averageMs()),
                new Queue(workers.workers(), workers.busy(), workers.waiting(), workers.queueCapacity()),
                jobs.size(),
                properties.retention().toMinutes(),
                catalog.stream().map(this::usage).toList());
    }

    private ConverterUsage usage(ConverterInfo info) {
        ConverterStats usage = stats.of(info.id());
        return new ConverterUsage(info.id(), info.title(), usage.succeeded(), usage.failed(), usage.averageMs());
    }

    record StatsResponse(Totals totals, Queue queue, int jobsStored, long retentionMinutes,
            List<ConverterUsage> converters) {
    }

    record Totals(long succeeded, long failed, long rejected, long averageMs) {
    }

    record Queue(int workers, int busy, int waiting, int capacity) {
    }

    record ConverterUsage(String id, String title, long succeeded, long failed, long averageMs) {
    }
}
