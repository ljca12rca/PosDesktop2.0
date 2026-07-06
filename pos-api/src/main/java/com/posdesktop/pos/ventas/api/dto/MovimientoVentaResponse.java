package com.posdesktop.pos.ventas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovimientoVentaResponse(
        UUID id,
        String numeroVenta,
        String origen,
        BigDecimal total,
        BigDecimal montoRecibido,
        BigDecimal cambioEntregado,
        LocalDateTime fechaVenta
) {
}
