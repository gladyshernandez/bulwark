package com.bulwark.screening;

import java.util.Optional;

/**
 * Talks to the Layer 3 LLM judge. Kept as an interface so the screening logic can be tested
 * against a fake without calling a real model.
 */
public interface JudgeClient {

    /**
     * Ask the judge whether {@code text} attempts a prompt injection.
     *
     * @return the judge's verdict and reason, or empty when the model couldn't be reached in
     *         time - the signal for Layer 3 to fail open.
     */
    Optional<Judgement> judge(String text);

    /** True when the judge is configured; false means Layer 3 is skipped. */
    boolean isEnabled();

    /**
     * The judge's decision on one input.
     *
     * @param injection true if the judge considers the input an injection attempt
     * @param reason     the judge's short rationale, logged for auditability
     */
    record Judgement(boolean injection, String reason) {}
}
