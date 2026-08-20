package com.bulwark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Layer 3 LLM judge, bound from {@code bulwark.layer3.*}.
 *
 * <p>The judge is the most expensive layer, so it stays off unless explicitly enabled and given
 * a key. It fires only on inputs the cheaper layers pass but can't clear: an input whose Layer 2
 * score is at or above {@code uncertaintyFloor} (but below the block threshold, or scored by no
 * classifier at all) is "uncertain" and worth a paid second opinion.
 *
 * @param enabled          master switch; the judge is skipped entirely when false
 * @param apiKey           Anthropic API key, defaulted from ANTHROPIC_API_KEY
 * @param model            the Claude model id to judge with, e.g. {@code claude-sonnet-5}
 * @param uncertaintyFloor Layer 2 score at or above which a passed input escalates to the judge
 * @param timeoutMillis    per-call timeout; on timeout the judge fails open
 */
@ConfigurationProperties(prefix = "bulwark.layer3")
public record Layer3Properties(
        boolean enabled,
        String apiKey,
        String model,
        double uncertaintyFloor,
        int timeoutMillis) {

    public Layer3Properties {
        if (uncertaintyFloor <= 0) {
            uncertaintyFloor = 0.2;
        }
        if (timeoutMillis <= 0) {
            timeoutMillis = 4000;
        }
    }

    /** True when the judge is switched on and has a key to call with; false skips Layer 3. */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
