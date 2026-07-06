package com.posdesktop.pos.articulos.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegistrarArticuloRequest(
        @NotBlank(message = "El nombre del articulo es obligatorio.")
        String nombre,
        String referencia,
        @NotNull(message = "El precio de venta es obligatorio.")
        @DecimalMin(value = "0.00", message = "El precio de venta no puede ser negativo.")
        BigDecimal precioVenta,
        boolean activo
) {
}
