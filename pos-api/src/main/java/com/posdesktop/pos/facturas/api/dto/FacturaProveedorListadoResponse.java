package com.posdesktop.pos.facturas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FacturaProveedorListadoResponse(
        String id,
        String proveedorId,
        String proveedorNombre,
        String proveedorNit,
        String numeroFactura,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        String estado,
        BigDecimal montoTotal,
        BigDecimal montoPagado,
        BigDecimal saldoPendiente,
        String observacion
) {
}
