package com.posdesktop.pos.separados.api.dto;

import com.posdesktop.pos.modelo.enumeraciones.MedioPagoVenta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegistrarAbonoSeparadoRequest(
        @NotNull(message = "El valor del abono es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor del abono debe ser mayor a cero.")
        BigDecimal valorAbono,
        String observacion,
        MedioPagoVenta medioPago
) {
}
