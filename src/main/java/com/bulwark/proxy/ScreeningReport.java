package com.bulwark.proxy;

import com.bulwark.screening.ScreeningDecision;
import com.bulwark.screening.ScreeningResult;

/**
 * Compact screening decision returned by {@code POST /v1/screen} - the verdict on an input without
 * forwarding it upstream. Used by the evaluation harness to record a decision per prompt cheaply.
 *
 * @param model         the requested model, echoed back (may be null)
 * @param action        allow / flag / block
 * @param blocked       true when the request would be refused (BLOCK)
 * @param layer         the layer that determined the decision
 * @param verdict       clean / injection / degraded
 * @param rule          the rule that fired, or null
 * @param evidence      the matched snippet or judge reason, or null
 * @param score         classifier score, or null
 * @param latencyMicros how long the deciding layer took
 */
public record ScreeningReport(
        String model,
        String action,
        boolean blocked,
        String layer,
        String verdict,
        String rule,
        String evidence,
        Double score,
        long latencyMicros) {

    public static ScreeningReport from(ScreeningResult result) {
        ScreeningDecision d = result.decision();
        return new ScreeningReport(
                result.model(),
                result.action().name(),
                result.isBlocked(),
                d.layer(),
                d.verdict().name(),
                d.rule(),
                d.evidence(),
                d.score(),
                d.latencyMicros());
    }
}
