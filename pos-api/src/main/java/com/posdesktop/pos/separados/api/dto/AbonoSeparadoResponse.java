package com.posdesktop.pos.separados.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AbonoSeparadoResponse(
        String id,
        Integer numeroAbono,
        LocalDateTime fechaAbono,
        BigDecimal montoAbono,
        boolean abonoInicial,
        String numeroVenta,
        String observacion,
        String responsableUsuario,
        String medioPago
) {
}
