package com.posdesktop.pos.facturas.api.dto;

import com.posdesktop.pos.modelo.enumeraciones.MetodoPagoFactura;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarPagoFacturaRequest(
        LocalDate fechaPago,
        @NotNull(message = "El valor del abono es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor del abono debe ser mayor a cero.")
        BigDecimal valorAbono,
        MetodoPagoFactura metodoPago,
        String referenciaPago,
        String observacion
) {
}
