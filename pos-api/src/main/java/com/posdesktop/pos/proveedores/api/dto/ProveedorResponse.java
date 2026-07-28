package com.posdesktop.pos.proveedores.api.dto;

import java.math.BigDecimal;

public record ProveedorResponse(
        String id,
        String nit,
        String nombre,
        String telefono,
        String correo,
        String direccion,
        String observacion,
        boolean activo,
        BigDecimal saldoPendienteTotal,
        int cantidadFacturas
) {
}
