package com.adit.mockDemo.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosMetricsTest {

    private ChaosMetrics metrics;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new ChaosMetrics(registry);
    }

    @Test
    void recordFailure_incrementsExperimentCounter() {
        metrics.recordFailure();
        metrics.recordFailure();

        assertThat(metrics.getExperimentFailures()).isEqualTo(2);
    }

    @Test
    void recordDelay_incrementsExperimentCounter() {
        metrics.recordDelay();

        assertThat(metrics.getExperimentDelays()).isEqualTo(1);
    }

    @Test
    void recordFailure_incrementsLifetimePrometheusCounter() {
        metrics.recordFailure();
        metrics.recordFailure();
        metrics.recordFailure();

        double count = registry.counter("chaoslab.experiment.failures.total", "application", "chaoslab").count();
        assertThat(count).isEqualTo(3.0);
    }

    @Test
    void recordDelay_incrementsLifetimePrometheusCounter() {
        metrics.recordDelay();

        double count = registry.counter("chaoslab.experiment.delays.total", "application", "chaoslab").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void prometheusGauges_areRegistered() {
        // These were NOT exposed before the fix — verify they exist
        assertThat(registry.find("chaoslab.experiment.failures.current").gauge()).isNotNull();
        assertThat(registry.find("chaoslab.experiment.delays.current").gauge()).isNotNull();
        assertThat(registry.find("chaoslab.experiment.traffic.current").gauge()).isNotNull();
    }

    @Test
    void prometheusGauge_reflectsLiveState() {
        metrics.recordFailure();
        metrics.recordFailure();

        double gaugeValue = registry.find("chaoslab.experiment.failures.current")
                .gauge().value();
        assertThat(gaugeValue).isEqualTo(2.0);
    }

    @Test
    void snapshotAndReset_returnsCorrectValuesAndResetsCounters() {
        metrics.recordFailure();
        metrics.recordFailure();
        metrics.recordDelay();
        metrics.setCurrentTraffic(50);

        ChaosMetrics.ChaosMetricsSnapshot snapshot = metrics.snapshotAndReset();

        assertThat(snapshot.failures()).isEqualTo(2);
        assertThat(snapshot.delays()).isEqualTo(1);
        assertThat(snapshot.traffic()).isEqualTo(50);

        // Experiment counters reset
        assertThat(metrics.getExperimentFailures()).isEqualTo(0);
        assertThat(metrics.getExperimentDelays()).isEqualTo(0);
    }

    @Test
    void snapshotAndReset_lifetimeCountersNotReset() {
        metrics.recordFailure();
        metrics.recordFailure();

        metrics.snapshotAndReset();

        // Lifetime counter must survive reset
        double lifetimeCount = registry.counter("chaoslab.experiment.failures.total",
                "application", "chaoslab").count();
        assertThat(lifetimeCount).isEqualTo(2.0);
    }

    @Test
    void resetExperiment_clearsAllExperimentState() {
        metrics.recordFailure();
        metrics.recordDelay();
        metrics.setCurrentTraffic(100);

        metrics.resetExperiment();

        assertThat(metrics.getExperimentFailures()).isEqualTo(0);
        assertThat(metrics.getExperimentDelays()).isEqualTo(0);
        assertThat(metrics.getCurrentTraffic()).isEqualTo(0);
    }

    @Test
    void gaugeValue_resetsAfterSnapshotAndReset() {
        metrics.recordFailure();
        metrics.snapshotAndReset();

        double gaugeValue = registry.find("chaoslab.experiment.failures.current")
                .gauge().value();
        assertThat(gaugeValue).isEqualTo(0.0);
    }
}