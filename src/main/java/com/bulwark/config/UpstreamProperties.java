package com.bulwark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the upstream LLM provider that Bulwark forwards to.
 * Bound from the {@code bulwark.upstream.*} properties (see application.yml).
 *
 * @param baseUrl the provider base URL, e.g. https://api.anthropic.com
 * @param apiKey  the provider API key, supplied via the ANTHROPIC_API_KEY env var
 */
@ConfigurationProperties(prefix = "bulwark.upstream")
public record UpstreamProperties(String baseUrl, String apiKey) {
}
