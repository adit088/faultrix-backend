package com.adit.mockDemo.metrics;

import com.adit.mockDemo.chaos.ChaosEngine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import java.util.concurrent.atomic.AtomicLong;



@Component
public class ChaosMetrics {

    // 🔹 Experiment-scoped state (RESETTABLE)
    private final AtomicLong experimentFailures = new AtomicLong();
    private final AtomicLong experimentDelays   = new AtomicLong();

    @Getter @Setter
    private volatile int currentTraffic = 0;

    // 🔹 Lifetime metrics (Micrometer)
    private final Counter failureCounter;
    private final Counter delayCounter;

    public ChaosMetrics(MeterRegistry registry){
        this.failureCounter = Counter.builder("chaos.failures.count")
                .description("Total chaos-induced failures")
                .register(registry);

        this.delayCounter = Counter.builder("chaos.delays.count")
                .description("Total chaos-induced delays")
                .register(registry);
    }

    public void recordFailure(){
        failureCounter.increment();              // observability
        experimentFailures.incrementAndGet();    // experiment
    }

    public void recordDelay(){
        delayCounter.increment();                // observability
        experimentDelays.incrementAndGet();      // experiment
    }

    public record ChaosMetricsSnapshot(long failures, long delays, int traffic){}

    public synchronized ChaosMetricsSnapshot snapshotAndReset() {

        long failures = experimentFailures.getAndSet(0);
        long delays   = experimentDelays.getAndSet(0);
        int traffic   = currentTraffic;

        currentTraffic = 0;

        return new ChaosMetricsSnapshot(failures, delays, traffic);
    }

    public long getExperimentFailures() {
        return experimentFailures.get();
    }

    public long getExperimentDelays() {
        return experimentDelays.get();
    }
    public void resetExperiment() {
        experimentFailures.set(0);
        experimentDelays.set(0);
        currentTraffic = 0;
    }


}
