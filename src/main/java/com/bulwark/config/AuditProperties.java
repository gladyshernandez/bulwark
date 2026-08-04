package com.bulwark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the append-only audit log.
 *
 * @param jdbcUrl  JDBC URL, e.g. {@code jdbc:postgresql://host:5432/bulwark}; enables audit when set
 * @param username database user
 * @param password database password
 */
@ConfigurationProperties(prefix = "bulwark.audit")
public record AuditProperties(String jdbcUrl, String username, String password) {
}
