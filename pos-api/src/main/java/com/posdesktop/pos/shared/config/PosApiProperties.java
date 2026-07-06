package com.posdesktop.pos.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pos.api")
public record PosApiProperties(
        String applicationName,
        String version,
        String environment,
        boolean desktopMockEnabled
) {
}
