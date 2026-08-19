package com.bulwark.config;

import com.bulwark.screening.AuditLog;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Wires the audit log. A DataSource is created only when {@code bulwark.audit.jdbc-url} is set;
 * without it there is no DataSource, {@link AuditLog} runs as a no-op, and startup proceeds. The
 * application excludes {@code DataSourceAutoConfiguration} so that owning DataSource creation here
 * keeps a missing or unreachable database from blocking startup.
 */
@Configuration
public class AuditConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bulwark.audit", name = "jdbc-url")
    DataSource auditDataSource(AuditProperties props) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(props.jdbcUrl())
                .username(props.username())
                .password(props.password())
                .build();
        ds.setPoolName("bulwark-audit");
        ds.setMaximumPoolSize(3);
        // Never let an unreachable Postgres block startup: connect lazily and let
        // runtime writes retry instead of failing the pool at initialization.
        ds.setInitializationFailTimeout(-1);
        return ds;
    }

    @Bean
    AuditLog auditLog(ObjectProvider<JdbcTemplate> jdbcTemplate) {
        // Null when audit is not configured
        return new AuditLog(jdbcTemplate.getIfAvailable());
    }
}
