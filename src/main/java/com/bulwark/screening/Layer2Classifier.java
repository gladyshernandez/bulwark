package com.bulwark.screening;

import com.bulwark.config.Layer2Properties;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Layer 2 — a pre-trained DeBERTa injection classifier served by a sidecar.
 *
 * <p>Runs only on inputs Layer 1 let through, adding coverage on paraphrased and obfuscated
 * injections at real (~tens of ms) latency. The sidecar returns an injection probability; an
 * input scoring at or above {@code bulwark.layer2.threshold} is an injection. When the sidecar
 * is unreachable the scan is {@link Verdict#DEGRADED} - the request fails open and the gap is
 * recorded rather than silently dropped.
 */
@Component
public class Layer2Classifier {

    public static final String LAYER = "layer2-deberta";
    public static final String RULE = "deberta-classifier";

    private final ClassifierClient client;
    private final double threshold;

    public Layer2Classifier(ClassifierClient client, Layer2Properties props) {
        this.client = client;
        this.threshold = props.threshold();
    }

    /** True when Layer 2 is configured and should run. */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /**
     * Classify {@code text} and return a scored decision, or a degraded one if the sidecar
     * couldn't be reached.
     */
    public ScreeningDecision scan(String text) {
        long start = System.nanoTime();
        OptionalDouble score = client.injectionScore(text);
        long elapsed = elapsedMicros(start);
        if (score.isEmpty()) {
            return ScreeningDecision.degraded(LAYER, elapsed);
        }
        double s = score.getAsDouble();
        return s >= threshold
                ? ScreeningDecision.injectionScored(LAYER, RULE, s, elapsed)
                : ScreeningDecision.cleanScored(LAYER, s, elapsed);
    }

    private static long elapsedMicros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000;
    }
}
