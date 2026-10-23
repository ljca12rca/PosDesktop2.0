package com.posdesktop.pos.facturacion.electronica.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pos.facturacion-electronica.dataico")
public record DataicoProperties(
        boolean enabled,
        String baseUrl,
        String accountId,
        String authToken,
        String configuredAuthToken,
        String prefix,
        String resolutionNumber,
        String initialNumber,
        boolean flexible,
        String environment,
        Duration connectTimeout,
        Duration readTimeout
) {
    public static final String DEFAULT_BASE_URL = "https://api.dataico.com/direct/dataico_api/v2";

    public void validarConsecutivo(long numero) {
        if (numero < 1) {
            throw new IllegalArgumentException("El consecutivo de Dataico debe ser un número positivo.");
        }
    }

    public String tokenParaProveedor() {
        return configuredAuthToken != null && !configuredAuthToken.isBlank()
                ? configuredAuthToken.trim()
                : authToken;
    }

    @Override public String toString() {
        return "DataicoProperties[enabled=" + enabled + ", prefix=" + prefix + ", environment=" + environment + ", token=***]";
    }
}
