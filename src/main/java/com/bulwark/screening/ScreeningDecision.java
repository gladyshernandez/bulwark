package com.bulwark.screening;

/**
 * The result of running one screening layer over a request.
 *
 * @param layer         layer name, e.g. {@code "layer1-regex"} or {@code "layer2-deberta"}
 * @param verdict       {@link Verdict#CLEAN}, {@link Verdict#INJECTION}, or {@link Verdict#DEGRADED}
 * @param rule          id of the rule that fired, or {@code null} when clean/degraded
 * @param evidence      the matched snippet (truncated), or {@code null} when there is none
 * @param score         classifier confidence in [0,1], or {@code null} for layers that don't score
 * @param latencyMicros wall-clock time this layer took, in microseconds
 */
public record ScreeningDecision(
        String layer,
        Verdict verdict,
        String rule,
        String evidence,
        Double score,
        long latencyMicros) {

    public boolean isInjection() {
        return verdict == Verdict.INJECTION;
    }

    /** True when the layer couldn't produce a verdict and the request fails open past it. */
    public boolean isDegraded() {
        return verdict == Verdict.DEGRADED;
    }

    public static ScreeningDecision clean(String layer, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.CLEAN, null, null, null, latencyMicros);
    }

    public static ScreeningDecision injection(
            String layer, String rule, String evidence, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.INJECTION, rule, evidence, null, latencyMicros);
    }

    /** A scored clean verdict from a classifier layer. */
    public static ScreeningDecision cleanScored(String layer, double score, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.CLEAN, null, null, score, latencyMicros);
    }

    /** A scored injection verdict from a classifier layer. */
    public static ScreeningDecision injectionScored(
            String layer, String rule, double score, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.INJECTION, rule, null, score, latencyMicros);
    }

    /** The layer couldn't run; the request fails open past it. */
    public static ScreeningDecision degraded(String layer, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.DEGRADED, null, null, null, latencyMicros);
    }
}
