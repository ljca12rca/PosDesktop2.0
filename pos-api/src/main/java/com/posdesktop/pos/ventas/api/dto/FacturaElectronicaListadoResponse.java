package com.posdesktop.pos.ventas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FacturaElectronicaListadoResponse(
        String id, LocalDateTime fechaEmision, String numeracion, String numeroFactura,
        String clienteNombre, String clienteIdentificacion, BigDecimal impuestoTotal, BigDecimal retencionTotal,
        BigDecimal total, String estadoDian, String estadoCliente, String estadoCorreo,
        String mensajeDian, String mensajeCorreo, String pdfUrl, String clienteEmail
) {
}
