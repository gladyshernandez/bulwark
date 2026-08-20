package com.bulwark.proxy;

import com.bulwark.screening.ScreeningDecision;
import com.bulwark.screening.ScreeningService;

import java.util.List;

/**
 * Each layer's independent verdict on one input, returned by {@code POST /v1/screen/layers}. Used
 * by the benchmark to measure per-layer detection - what each layer catches on its own, before the
 * escalation chain gates it.
 *
 * @param model  the requested model, echoed back (may be null)
 * @param layers one entry per enabled layer, in cheapest-first order
 */
public record PerLayerReport(String model, List<LayerReport> layers) {

    public static PerLayerReport from(ScreeningService.PerLayerResult result) {
        return new PerLayerReport(
                result.model(),
                result.layers().stream().map(LayerReport::from).toList());
    }

    /**
     * @param layer         layer name
     * @param flagged       true when this layer alone judged the input an injection
     * @param verdict       clean / injection / degraded
     * @param rule          the rule that fired, or null
     * @param score         classifier score, or null
     * @param latencyMicros how long this layer took
     */
    public record LayerReport(
            String layer,
            boolean flagged,
            String verdict,
            String rule,
            Double score,
            long latencyMicros) {

        static LayerReport from(ScreeningDecision d) {
            return new LayerReport(
                    d.layer(), d.isInjection(), d.verdict().name(), d.rule(), d.score(), d.latencyMicros());
        }
    }
}
