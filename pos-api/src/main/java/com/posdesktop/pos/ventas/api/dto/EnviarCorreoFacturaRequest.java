package com.posdesktop.pos.ventas.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarCorreoFacturaRequest(
        @NotBlank(message = "Ingresa el correo del destinatario.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres.")
        String email
) { }
