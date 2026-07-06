package com.posdesktop.pos.ventas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VentaRegistradaResponse(
        UUID id,
        String numeroVenta,
        LocalDateTime fechaVenta,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal montoRecibido,
        BigDecimal cambioEntregado,
        int cantidadDetalles,
        List<DetalleVentaResponse> detalles
) {
}
