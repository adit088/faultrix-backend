package com.adit.mockDemo.chaos.execution;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.service.ChaosEventService;
import com.adit.mockDemo.service.WebhookService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Logs chaos decisions, persists events, and fires webhooks.
 * All I/O (DB + HTTP) is async — this method returns in microseconds.
 *
 * Skipped events are sampled at 1% to prevent DB flood at high request rates.
 * At 1000 req/s with 70% skip rate, unsampled = 700 writes/sec; sampled = 7 writes/sec.
 */
@Component
@Slf4j
public class ChaosEventLogger {

    private static final double SKIPPED_SAMPLE_RATE = 0.01; // record 1% of skipped events

    private final Counter chaosInjectedCounter;
    private final Counter chaosSkippedCounter;
    private final Counter chaosSkippedSampledCounter;
    private final Timer   chaosLatencyTimer;
    private final ChaosEventService chaosEventService;
    private final WebhookService    webhookService;

    public ChaosEventLogger(MeterRegistry meterRegistry,
                            ChaosEventService chaosEventService,
                            WebhookService webhookService) {

        this.chaosEventService = chaosEventService;
        this.webhookService    = webhookService;

        this.chaosInjectedCounter = Counter.builder("chaoslab.chaos.injected")
                .description("Number of times chaos was injected")
                .tag("application", "chaoslab")
                .register(meterRegistry);

        this.chaosSkippedCounter = Counter.builder("chaoslab.chaos.skipped")
                .description("Number of times chaos was skipped (all, for metrics)")
                .tag("application", "chaoslab")
                .register(meterRegistry);

        this.chaosSkippedSampledCounter = Counter.builder("chaoslab.chaos.skipped.sampled")
                .description("Number of skipped events actually written to DB (sampled at 1%)")
                .tag("application", "chaoslab")
                .register(meterRegistry);

        this.chaosLatencyTimer = Timer.builder("chaoslab.chaos.latency")
                .description("Injected latency duration")
                .tag("application", "chaoslab")
                .register(meterRegistry);
    }

    public void logDecision(Organization org,
                            String target,
                            ChaosDecision decision,
                            String requestId) {

        if (decision.isShouldInjectChaos()) {
            logInjected(org, target, decision, requestId);
        } else {
            logSkipped(org, target, requestId);
        }
    }

    private void logInjected(Organization org,
                             String target,
                             ChaosDecision decision,
                             String requestId) {

        log.info("CHAOS INJECTED - Org: {}, Target: {}, Type: {}, Delay: {}ms, ErrorCode: {}, RequestId: {}",
                org.getSlug(), target, decision.getChaosType(),
                decision.getDelayMs(), decision.getErrorCode(), requestId);

        chaosInjectedCounter.increment();

        if (decision.getDelayMs() > 0) {
            chaosLatencyTimer.record(decision.getDelayMs(), TimeUnit.MILLISECONDS);
        }

        // Always persist injected events — these are the primary analytics data
        chaosEventService.recordEvent(org, target, requestId, decision, true);
        webhookService.fireInjectionWebhooks(org, target, requestId, decision);
    }

    private void logSkipped(Organization org, String target, String requestId) {
        log.trace("Chaos skipped - Org: {}, Target: {}, RequestId: {}",
                org.getSlug(), target, requestId);

        // Always increment the Prometheus counter (cheap, in-memory)
        chaosSkippedCounter.increment();

        // Only write to DB for 1% of skipped events to prevent write flood.
        // Skipped events are needed for injection rate calculation but don't need 100% fidelity.
        if (ThreadLocalRandom.current().nextDouble() < SKIPPED_SAMPLE_RATE) {
            chaosSkippedSampledCounter.increment();
            chaosEventService.recordEvent(org, target, requestId, ChaosDecision.noChaos(), false);
        }
    }
}