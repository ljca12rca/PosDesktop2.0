package com.posdesktop.pos.ventas.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DetalleVentaResponse(
        UUID id,
        int orden,
        String descripcion,
        BigDecimal cantidad,
        BigDecimal valorUnitario,
        BigDecimal subtotal,
        BigDecimal total
) {
}
