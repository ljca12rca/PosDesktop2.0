package com.posdesktop.pos.facturas.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarFacturaProveedorRequest(
        @NotBlank(message = "El proveedor es obligatorio.")
        String proveedorId,
        @NotBlank(message = "El numero de factura es obligatorio.")
        String numeroFactura,
        @NotNull(message = "La fecha de emision es obligatoria.")
        LocalDate fechaEmision,
        @NotNull(message = "El valor total es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor total debe ser mayor a cero.")
        BigDecimal valorTotal,
        @NotNull(message = "El saldo inicial es obligatorio.")
        @DecimalMin(value = "0.00", message = "El saldo inicial no puede ser negativo.")
        BigDecimal saldoInicial,
        String observacion
) {
}
