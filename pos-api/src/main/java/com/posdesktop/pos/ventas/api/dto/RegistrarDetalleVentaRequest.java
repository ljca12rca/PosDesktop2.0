package com.posdesktop.pos.ventas.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegistrarDetalleVentaRequest(
        String descripcion,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero.")
        BigDecimal cantidad,
        @NotNull(message = "El valor unitario es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor unitario debe ser mayor a cero.")
        BigDecimal valorUnitario
) {
}
