package com.bulwark.screening;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Screening configuration
 *
 * @param mode     {@link ScreeningMode#BLOCK} (refuse on detection) or
 *                 {@link ScreeningMode#FLAG} (log and forward). Defaults to BLOCK.
 * @param onDegrade what to do when a layer can't run: {@link FailMode#OPEN} (forward) or
 *                 {@link FailMode#CLOSED} (refuse). Defaults to OPEN.
 */
@ConfigurationProperties(prefix = "bulwark.screening")
public record ScreeningProperties(ScreeningMode mode, FailMode onDegrade) {

    public ScreeningProperties {
        if (mode == null) {
            mode = ScreeningMode.BLOCK;
        }
        if (onDegrade == null) {
            onDegrade = FailMode.OPEN;
        }
    }
}
