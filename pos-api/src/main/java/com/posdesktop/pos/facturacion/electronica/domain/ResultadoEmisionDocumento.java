package com.posdesktop.pos.facturacion.electronica.domain;

/** Respuesta normalizada para no propagar DTOs de un proveedor al dominio. */
public record ResultadoEmisionDocumento(
        String proveedor,
        String identificadorExterno,
        String numero,
        String estado,
        String cufe,
        String qrCodeUrl,
        String pdfUrl,
        String mensaje,
        String estadoCorreo
) {
}
