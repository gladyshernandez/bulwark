package com.bulwark.screening;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Screening configuration
 *
 * @param mode           {@link ScreeningMode#BLOCK} (refuse on detection) or
 *                       {@link ScreeningMode#FLAG} (log and forward). Defaults to BLOCK.
 * @param onDegrade      what to do when a layer can't run: {@link FailMode#OPEN} (forward) or
 *                       {@link FailMode#CLOSED} (refuse). Defaults to OPEN.
 * @param indirectFields request field names (e.g. {@code documents}, {@code context}) whose text
 *                       is screened as untrusted retrieved content, not just the user message.
 *                       Empty means only message content is screened.
 */
@ConfigurationProperties(prefix = "bulwark.screening")
public record ScreeningProperties(ScreeningMode mode, FailMode onDegrade, List<String> indirectFields) {

    public ScreeningProperties {
        if (mode == null) {
            mode = ScreeningMode.BLOCK;
        }
        if (onDegrade == null) {
            onDegrade = FailMode.OPEN;
        }
        indirectFields = indirectFields == null ? List.of() : List.copyOf(indirectFields);
    }
}
