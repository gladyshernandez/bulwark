package com.bulwark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Layer 2 DeBERTa classifier sidecar, bound from {@code bulwark.layer2.*}.
 *
 * @param url           base URL of the classifier sidecar; Layer 2 is disabled when this is blank
 * @param threshold     score at or above which Layer 2 blocks on its own; lower scores that are
 *                      still at or above the Layer 3 floor are handed to the judge to decide
 * @param timeoutMillis connect/read timeout for a classify call; on timeout Layer 2 fails open
 */
@ConfigurationProperties(prefix = "bulwark.layer2")
public record Layer2Properties(String url, double threshold, int timeoutMillis) {

    public Layer2Properties {
        if (threshold <= 0) {
            threshold = 0.9;
        }
        if (timeoutMillis <= 0) {
            timeoutMillis = 800;
        }
    }

    /** True when a sidecar URL is configured; false means Layer 2 is skipped entirely. */
    public boolean isEnabled() {
        return url != null && !url.isBlank();
    }
}
