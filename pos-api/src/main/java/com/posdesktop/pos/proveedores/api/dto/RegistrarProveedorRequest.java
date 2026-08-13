package com.posdesktop.pos.proveedores.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistrarProveedorRequest(
        String nit,
        @NotBlank(message = "El nombre del proveedor es obligatorio.")
        String nombre,
        String telefono,
        @Email(message = "El correo del proveedor no es valido.")
        String correo,
        String direccion,
        String observacion
) {
}
