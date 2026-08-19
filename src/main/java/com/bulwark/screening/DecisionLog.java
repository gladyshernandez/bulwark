package com.bulwark.screening;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Structured, per-request decision log. Emits one line per screened request with
 * the layer, verdict, matched rule, action, and latency, plus a short hash of the
 * input so decisions can be correlated without logging prompt content.
 * This SLF4J log is the always-on record and the source of the fields that get persisted.
 */
@Component
public class DecisionLog {

    private static final Logger log = LoggerFactory.getLogger("bulwark.decision");

    public void record(String inputText, ScreeningDecision decision, ScreeningMode mode, Action action) {
        log.info("layer={} verdict={} rule={} score={} action={} mode={} latencyMicros={} inputHash={}",
                decision.layer(),
                decision.verdict(),
                decision.rule(),
                decision.score(),
                action,
                mode,
                decision.latencyMicros(),
                inputHashPrefix(inputText));
    }

    /** First 12 hex chars of the SHA-256 of the input - enough to correlate */
    static String inputHashPrefix(String inputText) {
        if (inputText == null) {
            inputText = "";
        }
        try {
            // Get an instance of SHA-256 hashing algo
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(inputText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12); //convert to hex and truncate
        } catch (NoSuchAlgorithmException e) {
            return "nohash";
        }
    }
}