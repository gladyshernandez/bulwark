package com.bulwark.screening;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Screening configuration
 *
 * @param mode {@link ScreeningMode#BLOCK} (refuse on detection) or
 *             {@link ScreeningMode#FLAG} (log and forward). Defaults to BLOCK.
 */
@ConfigurationProperties(prefix = "bulwark.screening")
public record ScreeningProperties(ScreeningMode mode) {

    public ScreeningProperties {
        if (mode == null) {
            mode = ScreeningMode.BLOCK;
        }
    }
}
