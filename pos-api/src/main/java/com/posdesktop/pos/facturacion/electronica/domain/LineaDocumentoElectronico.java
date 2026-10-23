package com.posdesktop.pos.facturacion.electronica.domain;

import java.math.BigDecimal;

public record LineaDocumentoElectronico(
        String codigo,
        String descripcion,
        BigDecimal cantidad,
        String unidadMedida,
        BigDecimal precioUnitario,
        BigDecimal porcentajeIva
) {
}
