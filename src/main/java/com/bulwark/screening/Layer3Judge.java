package com.bulwark.screening;

import com.bulwark.config.Layer3Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Layer 3 - an LLM judge for the hard cases, and the most expensive layer.
 *
 * <p>It runs only on inputs the cheaper layers pass but can't clear ({@link #shouldJudge}). When the
 * judge can't be reached the scan is {@link Verdict#DEGRADED} and the request fails open.
 */
@Component
public class Layer3Judge {

    public static final String LAYER = "layer3-judge";
    public static final String RULE = "llm-judge";

    private static final Logger log = LoggerFactory.getLogger("bulwark.judge");

    private final JudgeClient client;
    private final double floor;

    public Layer3Judge(JudgeClient client, Layer3Properties props) {
        this.client = client;
        this.floor = props.uncertaintyFloor();
    }

    /** True when Layer 3 is configured and should run. */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /**
     * Whether the judge should run, given Layer 2's decision ({@code null} when Layer 2 didn't run).
     * An input is uncertain - and worth the judge - when Layer 2 scored it at or above the floor, or
     * left no score to lean on; a score below the floor is confidently clean and skips the judge.
     */
    public boolean shouldJudge(ScreeningDecision layer2Decision) {
        if (!isEnabled()) {
            return false;
        }
        if (layer2Decision == null || layer2Decision.score() == null) {
            return true;
        }
        return layer2Decision.score() >= floor;
    }

    /** Judge {@code text}, or return a degraded decision if the judge couldn't be reached. */
    public ScreeningDecision scan(String text) {
        long start = System.nanoTime();
        Optional<JudgeClient.Judgement> judgement = client.judge(text);
        long elapsed = elapsedMicros(start);
        if (judgement.isEmpty()) {
            return ScreeningDecision.degraded(LAYER, elapsed);
        }
        JudgeClient.Judgement j = judgement.get();
        if (j.injection()) {
            log.info("layer=layer3-judge verdict=INJECTION reason=\"{}\"", j.reason());
            return ScreeningDecision.injection(LAYER, RULE, j.reason(), elapsed);
        }
        return ScreeningDecision.clean(LAYER, elapsed);
    }

    private static long elapsedMicros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000;
    }
}
