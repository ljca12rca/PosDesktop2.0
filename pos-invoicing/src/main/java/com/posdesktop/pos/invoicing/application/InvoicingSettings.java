package com.posdesktop.pos.invoicing.application;

import java.util.Objects;

/** Configuracion por instalacion, sin secretos. No habilita capacidades de emision. */
public record InvoicingSettings(String installationId, ProviderType provider) {
    public InvoicingSettings {
        if (installationId == null || installationId.isBlank()) throw new IllegalArgumentException("installationId requerido");
        provider = Objects.requireNonNullElse(provider, ProviderType.DATAICO);
    }
}
