package com.posdesktop.pos.clientes.api.dto;

public record ClienteElectronicoResponse(
        String id, String tipoIdentificacion, String tipoPersona, String identificacion, String digitoVerificacion,
        String razonSocial, String primerNombre, String segundoNombre, String primerApellido, String segundoApellido,
        String email, String telefono, String direccion, String ciudadCodigoDane,
        String departamentoCodigoDane, String paisCodigo
) { }
