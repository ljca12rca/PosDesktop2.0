package com.posdesktop.pos.facturacion.electronica.domain;

/** Datos de un tercero retornados por la consulta DIAN de Dataico. */
public record TerceroDian(
        String tipoPersona,
        String tipoIdentificacion,
        String identificacion,
        String digitoVerificacion,
        String razonSocial,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        String email,
        String telefono,
        String direccion,
        String ciudadCodigoDane,
        String departamentoCodigoDane,
        String paisCodigo
) {
}
