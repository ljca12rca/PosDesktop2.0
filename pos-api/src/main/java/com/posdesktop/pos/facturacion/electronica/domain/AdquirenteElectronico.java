package com.posdesktop.pos.facturacion.electronica.domain;

public record AdquirenteElectronico(
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
        DireccionFiscal direccion
) {
}
