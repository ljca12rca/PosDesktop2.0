package com.posdesktop.pos.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pos.api.documentos")
public record DocumentoStorageProperties(
        String storageRoot
) {
}
