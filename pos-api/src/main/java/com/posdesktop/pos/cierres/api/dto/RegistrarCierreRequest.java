package com.posdesktop.pos.cierres.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarCierreRequest(
        @NotNull(message = "La fecha del cierre es obligatoria.")
        LocalDate fechaOperacion,
        @NotBlank(message = "El responsable es obligatorio.")
        String responsable,
        @NotNull(message = "La base es obligatoria.")
        @DecimalMin(value = "0.00", message = "La base no puede ser negativa.")
        BigDecimal base,
        @NotNull(message = "Los egresos son obligatorios.")
        @DecimalMin(value = "0.00", message = "Los egresos no pueden ser negativos.")
        BigDecimal egresos,
        String observacion
) {
}
