package com.posdesktop.pos.cierres.service;

import java.math.BigDecimal;

public record CierreDiarioCalculo(
        int cantidadVentas,
        BigDecimal subtotalVentas,
        BigDecimal descuentoVentas,
        BigDecimal impuestoVentas,
        BigDecimal totalVentas,
        BigDecimal montoRecibido,
        BigDecimal cambioEntregado,
        BigDecimal montoNetoCaja,
        BigDecimal baseCaja,
        BigDecimal trabajadoras,
        BigDecimal ahorro,
        BigDecimal totalFinal
) {
}
