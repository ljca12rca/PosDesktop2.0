package com.posdesktop.pos.facturas.api.dto;

public record ProveedorFacturaResponse(
        String id,
        String nit,
        String nombre,
        String telefono,
        String correo
) {
}
