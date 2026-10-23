package com.posdesktop.pos.clientes.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegistrarClienteElectronicoRequest(
        @NotBlank(message = "El tipo de identificación es obligatorio.") String tipoIdentificacion,
        @Pattern(regexp = "JURIDICA|NATURAL", message = "La naturaleza debe ser JURIDICA o NATURAL.") String tipoPersona,
        @NotBlank(message = "La identificación del cliente es obligatoria.") String identificacion,
        String digitoVerificacion,
        String razonSocial,
        String primerNombre, String segundoNombre, String primerApellido, String segundoApellido,
        @Email(message = "El correo del cliente no es válido.") String email,
        String telefono, String direccion, String ciudadCodigoDane, String departamentoCodigoDane, String paisCodigo
) { }
