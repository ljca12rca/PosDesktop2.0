package com.posdesktop.pos.cierres.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarCierreRequest(
        @NotNull(message = "La fecha del cierre es obligatoria.")
        LocalDate fechaOperacion,
        @NotNull(message = "La base es obligatoria.")
        @DecimalMin(value = "0.00", message = "La base no puede ser negativa.")
        BigDecimal base,
        @NotNull(message = "El valor de trabajadoras es obligatorio.")
        @DecimalMin(value = "0.00", message = "El valor de trabajadoras no puede ser negativo.")
        BigDecimal trabajadoras,
        @NotNull(message = "El ahorro es obligatorio.")
        @DecimalMin(value = "0.00", message = "El ahorro no puede ser negativo.")
        BigDecimal ahorro,
        String observacion
) {
}
