package com.posdesktop.pos.ventas.api.dto;

public record ResultadoFacturaElectronicaVentaResponse(
        String identificadorExterno,
        String numero,
        String estadoDian,
        String cufe,
        String qrCode,
        String mensaje,
        String pdfUrl
) {
}
