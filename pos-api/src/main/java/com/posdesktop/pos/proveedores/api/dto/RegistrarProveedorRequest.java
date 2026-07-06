package com.posdesktop.pos.proveedores.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistrarProveedorRequest(
        @NotBlank(message = "El nombre del proveedor es obligatorio.")
        String nombre,
        String telefono,
        String correo,
        String observacion
) {
}
