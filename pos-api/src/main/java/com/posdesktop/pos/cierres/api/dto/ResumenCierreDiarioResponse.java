package com.posdesktop.pos.cierres.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenCierreDiarioResponse(
        LocalDate fechaOperacion,
        int cantidadVentas,
        BigDecimal subtotalVentas,
        BigDecimal totalVentas,
        BigDecimal montoRecibido,
        BigDecimal cambioEntregado,
        BigDecimal baseCaja,
        BigDecimal egresos,
        BigDecimal totalFinal,
        boolean cierreGuardado,
        String responsable,
        String estado,
        String observacion
) {
}
