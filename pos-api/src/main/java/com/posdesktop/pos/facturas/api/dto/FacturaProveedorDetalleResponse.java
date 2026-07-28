package com.posdesktop.pos.facturas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaProveedorDetalleResponse(
        String id,
        ProveedorFacturaResponse proveedor,
        String numeroFactura,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        String estado,
        BigDecimal montoTotal,
        BigDecimal montoPagado,
        BigDecimal saldoPendiente,
        String observacion,
        List<DocumentoSoporteResponse> soportesFactura,
        List<PagoFacturaResponse> abonos
) {
}
