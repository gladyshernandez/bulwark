package com.bulwark.screening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Append-only audit sink. Persists one row per screened request - input hash (never
 * the prompt itself), per-layer verdict, matched rule, classifier score, action, mode,
 * and latency - to Postgres via plain JDBC.
 *
 * <p>Audit is strictly best-effort. When no database is configured the {@link JdbcTemplate}
 * is {@code null} and every call is a no-op; when the database is configured but unreachable,
 * writes fail quietly and the proxied request proceeds regardless. A screening decision is
 * never lost to a database problem - the always-on record is the SLF4J {@link DecisionLog}.
 */
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS bulwark_audit (
                id             BIGSERIAL PRIMARY KEY,
                ts             TIMESTAMPTZ NOT NULL DEFAULT now(),
                input_hash     VARCHAR(12) NOT NULL,
                layer          VARCHAR(32) NOT NULL,
                verdict        VARCHAR(16) NOT NULL,
                rule           VARCHAR(64),
                score          DOUBLE PRECISION,
                action         VARCHAR(16) NOT NULL,
                mode           VARCHAR(16) NOT NULL,
                latency_micros BIGINT NOT NULL
            )
            """;

    // Migrate audit tables created before Layer 2 added the classifier score column.
    private static final String ADD_SCORE =
            "ALTER TABLE bulwark_audit ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION";

    private static final String INSERT = """
            INSERT INTO bulwark_audit
                (input_hash, layer, verdict, rule, score, action, mode, latency_micros)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Null when audit is not configured. */
    private final JdbcTemplate jdbc;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);
    private volatile boolean warnedUnavailable = false;

    public AuditLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True when an audit database is configured; false means every write is a no-op. */
    public boolean isEnabled() {
        return jdbc != null;
    }

    public void record(String inputText, ScreeningDecision decision, ScreeningMode mode, Action action) {
        if (jdbc == null) {
            return;
        }
        try {
            ensureSchema();
            jdbc.update(INSERT,
                    DecisionLog.inputHashPrefix(inputText),
                    decision.layer(),
                    decision.verdict().name(),
                    decision.rule(),
                    decision.score(),
                    action.name(),
                    mode.name(),
                    decision.latencyMicros());
        } catch (DataAccessException e) {
            // Best-effort: a database hiccup must never fail a proxied request.
            if (!warnedUnavailable) {
                log.warn("Audit write failed; continuing without persistence: {}", e.getMessage());
                warnedUnavailable = true;
            }
        }
    }

    /** Create the table on first successful use; retried on later calls if it fails. */
    private void ensureSchema() {
        if (schemaReady.get()) {
            return;
        }
        jdbc.execute(DDL);
        jdbc.execute(ADD_SCORE);
        schemaReady.set(true);
    }
}
