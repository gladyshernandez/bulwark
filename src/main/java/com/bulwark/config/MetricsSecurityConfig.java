package com.bulwark.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link MetricsAuthFilter} on the Prometheus endpoint only.
 */
@Configuration
public class MetricsSecurityConfig {

    @Bean
    public FilterRegistrationBean<MetricsAuthFilter> metricsAuthFilter(MetricsProperties properties) {
        FilterRegistrationBean<MetricsAuthFilter> registration =
                new FilterRegistrationBean<>(new MetricsAuthFilter(properties.token()));
        registration.addUrlPatterns("/actuator/prometheus");
        return registration;
    }
}
