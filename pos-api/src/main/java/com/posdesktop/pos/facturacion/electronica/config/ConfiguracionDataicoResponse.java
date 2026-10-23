package com.posdesktop.pos.facturacion.electronica.config;

public record ConfiguracionDataicoResponse(
        String prefijo, String resolucion, Long siguienteNumero, String ambiente, boolean flexible,
        Long version, String accountId, boolean proveedorHabilitado, boolean tokenConfigurado,
        String baseUrl, int connectTimeoutSeconds, int readTimeoutSeconds
) { }
