package com.posdesktop.pos.facturacion.electronica.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record GuardarConfiguracionDataicoRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9]+") String prefijo,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[0-9]+") String resolucion,
        @NotNull @Positive Long siguienteNumero,
        @NotBlank @Pattern(regexp = "PRODUCCION|PRUEBAS") String ambiente,
        @NotNull Boolean flexible,
        @NotNull @PositiveOrZero Long version,
        @NotNull @Positive Long siguienteNumeroActual,
        @Size(max = 512, message = "El token no puede superar 512 caracteres.") String token,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", message = "El Account ID debe ser un UUID válido.") String accountId,
        Boolean proveedorHabilitado,
        @Size(max = 200) String baseUrl,
        @Min(1) @Max(10) Integer connectTimeoutSeconds,
        @Min(1) @Max(30) Integer readTimeoutSeconds
) {
    public GuardarConfiguracionDataicoRequest(String prefijo, String resolucion, Long siguienteNumero, String ambiente,
            Boolean flexible, Long version, Long siguienteNumeroActual) {
        this(prefijo, resolucion, siguienteNumero, ambiente, flexible, version, siguienteNumeroActual, null);
    }
    public GuardarConfiguracionDataicoRequest(String prefijo, String resolucion, Long siguienteNumero, String ambiente,
            Boolean flexible, Long version, Long siguienteNumeroActual, String token) {
        this(prefijo, resolucion, siguienteNumero, ambiente, flexible, version, siguienteNumeroActual, token, null, null, null, null, null);
    }
    @Override public String toString() { return "GuardarConfiguracionDataicoRequest[prefijo=" + prefijo + ", token=***]"; }
}
