package com.posdesktop.pos.ventas.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record RegistrarVentaManualRequest(
        @NotEmpty(message = "La venta debe contener al menos un detalle.")
        List<@Valid RegistrarDetalleVentaRequest> detalles,
        @DecimalMin(value = "0.00", message = "El monto recibido no puede ser negativo.")
        BigDecimal montoRecibido,
        String observacion
) {
}
