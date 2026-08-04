package com.posdesktop.pos.separados.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeparadoListadoResponse(
        String id,
        String numeroSeparado,
        String cliente,
        String descripcionArticulos,
        String estado,
        BigDecimal valorTotal,
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente,
        LocalDate fechaSeparacion,
        String responsableUsuario
) {
}
