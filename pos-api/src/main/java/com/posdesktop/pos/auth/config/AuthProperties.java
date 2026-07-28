package com.posdesktop.pos.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pos.auth")
public record AuthProperties(
        int sessionHours
) {
}
