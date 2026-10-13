package com.assignment.agentic.orchestrator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OrchestrationMetrics {

    private final Counter success;
    private final Counter failure;
    private final Counter retries;
    private final Counter rollbacks;
    private final Timer latency;
    private final AtomicInteger inFlight = new AtomicInteger();

    public OrchestrationMetrics(MeterRegistry registry) {
        this.success = registry.counter("orchestrator.runs.success");
        this.failure = registry.counter("orchestrator.runs.failure");
        this.retries = registry.counter("orchestrator.stage.retries");
        this.rollbacks = registry.counter("orchestrator.stage.rollbacks");
        this.latency = registry.timer("orchestrator.runs.latency");
        registry.gauge("orchestrator.runs.inflight", inFlight);
    }

    public void runStarted() {
        inFlight.incrementAndGet();
    }

    public void runFinished(boolean ok, Duration duration) {
        inFlight.decrementAndGet();
        if (ok) {
            success.increment();
        } else {
            failure.increment();
        }
        latency.record(duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void retry() {
        retries.increment();
    }

    public void rollback() {
        rollbacks.increment();
    }

    public double successRate() {
        double s = success.count();
        double f = failure.count();
        if (s + f == 0) {
            return 0;
        }
        return s / (s + f);
    }

    public double retryCount() {
        return retries.count();
    }

    public double rollbackCount() {
        return rollbacks.count();
    }

    public double meanLatencyMs() {
        return latency.mean(TimeUnit.MILLISECONDS);
    }
}
