package com.posdesktop.pos.separados.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SeparadoDetalleResponse(
        String id,
        String numeroSeparado,
        String cliente,
        String telefonoCliente,
        String descripcionArticulos,
        String estado,
        BigDecimal valorTotal,
        BigDecimal montoMinimoInicial,
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente,
        LocalDate fechaSeparacion,
        LocalDate fechaEntrega,
        String observacion,
        List<AbonoSeparadoResponse> abonos
) {
}
