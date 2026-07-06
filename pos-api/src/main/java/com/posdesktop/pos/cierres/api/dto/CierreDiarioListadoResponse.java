package com.posdesktop.pos.cierres.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CierreDiarioListadoResponse(
        UUID id,
        LocalDate fechaOperacion,
        LocalDateTime fechaHoraCierre,
        String responsable,
        int cantidadVentas,
        BigDecimal totalVentas,
        BigDecimal baseCaja,
        BigDecimal egresos,
        BigDecimal totalFinal,
        String estado
) {
}
