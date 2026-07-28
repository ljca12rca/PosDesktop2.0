package com.posdesktop.pos.facturas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PagoFacturaResponse(
        String id,
        LocalDate fechaPago,
        BigDecimal montoPago,
        String metodoPago,
        String referenciaPago,
        String observacion,
        BigDecimal saldoRestante,
        List<DocumentoSoporteResponse> soportes
) {
}
