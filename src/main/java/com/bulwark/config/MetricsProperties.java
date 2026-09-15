package com.bulwark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the metrics endpoint guard, bound from {@code bulwark.metrics.*}.
 *
 * @param token bearer token required to read {@code /actuator/prometheus}; blank leaves it open
 */
@ConfigurationProperties(prefix = "bulwark.metrics")
public record MetricsProperties(String token) {
}
