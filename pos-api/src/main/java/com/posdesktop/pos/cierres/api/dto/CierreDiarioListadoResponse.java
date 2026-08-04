package com.posdesktop.pos.cierres.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CierreDiarioListadoResponse(
        UUID id,
        LocalDate fechaOperacion,
        LocalDateTime fechaHoraCierre,
        int cantidadVentas,
        BigDecimal totalVentas,
        BigDecimal montoNetoCaja,
        BigDecimal baseCaja,
        BigDecimal trabajadoras,
        BigDecimal ahorro,
        BigDecimal totalFinal,
        String estado,
        String responsableUsuario
) {
}
