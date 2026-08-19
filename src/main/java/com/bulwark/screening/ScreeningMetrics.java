package com.bulwark.screening;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Publishes per-request screening metrics to Micrometer, scraped from
 * {@code /actuator/prometheus} onto the public Grafana dashboard:
 *
 * <ul>
 *   <li>{@code bulwark.screening.decisions} - counter tagged by layer, verdict, action, mode</li>
 *   <li>{@code bulwark.screening.latency} - timer tagged by layer</li>
 * </ul>
 *
 * Tag values come from bounded enums, so cardinality stays small as layers are added.
 */
@Component
public class ScreeningMetrics {

    private final MeterRegistry registry;

    public ScreeningMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(ScreeningDecision decision, ScreeningMode mode, Action action) {
        registry.counter("bulwark.screening.decisions",
                "layer", decision.layer(),
                "verdict", decision.verdict().name().toLowerCase(),
                "action", action.name().toLowerCase(),
                "mode", mode.name().toLowerCase()).increment();

        registry.timer("bulwark.screening.latency",
                "layer", decision.layer()).record(decision.latencyMicros(), TimeUnit.MICROSECONDS);
    }
}
