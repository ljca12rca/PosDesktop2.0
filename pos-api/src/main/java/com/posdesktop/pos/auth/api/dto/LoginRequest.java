package com.posdesktop.pos.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El usuario es obligatorio.")
        String username,
        @NotBlank(message = "La clave es obligatoria.")
        String password
) {
}
