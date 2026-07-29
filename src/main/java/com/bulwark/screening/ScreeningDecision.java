package com.bulwark.screening;

/**
 * The result of running one screening layer over a request.
 *
 * @param layer         layer name, e.g. {@code "layer1-regex"}
 * @param verdict       {@link Verdict#CLEAN} or {@link Verdict#INJECTION}
 * @param rule          id of the rule that fired, or {@code null} when clean
 * @param evidence      the matched snippet (truncated), or {@code null} when clean
 * @param latencyMicros wall-clock time this layer took, in microseconds
 */
public record ScreeningDecision(
        String layer,
        Verdict verdict,
        String rule,
        String evidence,
        long latencyMicros) {

    public boolean isInjection() {
        return verdict == Verdict.INJECTION;
    }

    public static ScreeningDecision clean(String layer, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.CLEAN, null, null, latencyMicros);
    }

    public static ScreeningDecision injection(
            String layer, String rule, String evidence, long latencyMicros) {
        return new ScreeningDecision(layer, Verdict.INJECTION, rule, evidence, latencyMicros);
    }
}