package com.adit.mockDemo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chaos experiment metrics — both resettable experiment-scoped counters
 * and lifetime Prometheus counters exposed via Micrometer.
 *
 * All experiment AtomicLong/AtomicInteger values are wired to Gauges so
 * Prometheus scrapes the live experiment state (current failures, delays,
 * traffic) in addition to the lifetime counters.
 *
 * Thread-safety note: currentTraffic is now an AtomicInteger so that
 * snapshotAndReset() reads and resets it atomically in its synchronized
 * block without a data race against setCurrentTraffic().
 */
@Component
public class ChaosMetrics {

    // ── Experiment-scoped state (resettable) ─────────────────────────────────
    private final AtomicLong    experimentFailures = new AtomicLong(0);
    private final AtomicLong    experimentDelays   = new AtomicLong(0);
    private final AtomicLong    experimentTraffic  = new AtomicLong(0);
    private final AtomicInteger currentTraffic     = new AtomicInteger(0);

    // ── Lifetime counters (Micrometer — never reset) ─────────────────────────
    private final Counter failureCounter;
    private final Counter delayCounter;

    public ChaosMetrics(MeterRegistry registry) {
        this.failureCounter = Counter.builder("chaoslab.experiment.failures.total")
                .description("Lifetime total chaos-induced failures across all experiments")
                .tag("application", "chaoslab")
                .register(registry);

        this.delayCounter = Counter.builder("chaoslab.experiment.delays.total")
                .description("Lifetime total chaos-induced delays across all experiments")
                .tag("application", "chaoslab")
                .register(registry);

        // Wire experiment-scoped atomics as Gauges so Prometheus can see live state
        Gauge.builder("chaoslab.experiment.failures.current",
                        experimentFailures, AtomicLong::doubleValue)
                .description("Failures in the current active experiment (resets on snapshotAndReset)")
                .tag("application", "chaoslab")
                .register(registry);

        Gauge.builder("chaoslab.experiment.delays.current",
                        experimentDelays, AtomicLong::doubleValue)
                .description("Delays in the current active experiment (resets on snapshotAndReset)")
                .tag("application", "chaoslab")
                .register(registry);

        Gauge.builder("chaoslab.experiment.traffic.current",
                        experimentTraffic, AtomicLong::doubleValue)
                .description("Active simulated traffic users in current experiment")
                .tag("application", "chaoslab")
                .register(registry);
    }

    // ── Record events ────────────────────────────────────────────────────────

    public void recordFailure() {
        failureCounter.increment();           // lifetime — never reset
        experimentFailures.incrementAndGet(); // experiment-scoped — resets
    }

    public void recordDelay() {
        delayCounter.increment();           // lifetime — never reset
        experimentDelays.incrementAndGet(); // experiment-scoped — resets
    }

    // ── Accessors for currentTraffic (API unchanged — tests compile as-is) ──

    /** Set the number of simulated concurrent users for the current experiment. */
    public void setCurrentTraffic(int traffic) {
        currentTraffic.set(traffic);
    }

    /** Read current simulated traffic count. */
    public int getCurrentTraffic() {
        return currentTraffic.get();
    }

    // ── Experiment snapshot & reset ──────────────────────────────────────────

    public record ChaosMetricsSnapshot(long failures, long delays, int traffic) {}

    /**
     * Atomically capture and zero all experiment-scoped counters.
     * synchronized ensures the three getAndSet calls + AtomicInteger.getAndSet
     * form one coherent snapshot with no interleaved writes from other threads.
     */
    public synchronized ChaosMetricsSnapshot snapshotAndReset() {
        long failures = experimentFailures.getAndSet(0);
        long delays   = experimentDelays.getAndSet(0);
        int  traffic  = currentTraffic.getAndSet(0); // atomic read-and-zero

        experimentTraffic.set(0);

        return new ChaosMetricsSnapshot(failures, delays, traffic);
    }

    public void resetExperiment() {
        experimentFailures.set(0);
        experimentDelays.set(0);
        experimentTraffic.set(0);
        currentTraffic.set(0);
    }

    // ── Read current state ───────────────────────────────────────────────────

    public long getExperimentFailures() { return experimentFailures.get(); }
    public long getExperimentDelays()   { return experimentDelays.get();   }
}