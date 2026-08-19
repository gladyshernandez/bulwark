package com.bulwark.screening;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ScreeningMetricsTest {

    @Test
    void countsDecisionsTaggedByLayerVerdictActionAndMode() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScreeningMetrics metrics = new ScreeningMetrics(registry);

        ScreeningDecision decision =
                ScreeningDecision.injection("layer1-regex", "override", "ignore all previous", 42);
        metrics.record(decision, ScreeningMode.BLOCK, Action.BLOCK);

        double count = registry.get("bulwark.screening.decisions")
                .tags("layer", "layer1-regex", "verdict", "injection", "action", "block", "mode", "block")
                .counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void recordsLatencyTimerPerLayer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScreeningMetrics metrics = new ScreeningMetrics(registry);

        metrics.record(ScreeningDecision.clean("layer1-regex", 100), ScreeningMode.FLAG, Action.ALLOW);

        var timer = registry.get("bulwark.screening.latency").tag("layer", "layer1-regex").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MICROSECONDS)).isEqualTo(100.0);
    }
}
