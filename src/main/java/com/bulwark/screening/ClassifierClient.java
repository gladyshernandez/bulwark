package com.bulwark.screening;

import java.util.OptionalDouble;

/**
 * Talks to the Layer 2 classifier. Kept as an interface so the screening logic can be
 * tested against a fake without a running sidecar.
 */
public interface ClassifierClient {

    /**
     * Score {@code text} for prompt injection.
     *
     * @return the injection probability in [0,1], or empty when the sidecar couldn't be
     *         reached in time - the signal for Layer 2 to fail open.
     */
    OptionalDouble injectionScore(String text);

    /** True when a sidecar is configured; false means Layer 2 is skipped. */
    boolean isEnabled();
}
