package com.posdesktop.pos.separados.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegistrarSeparadoRequest(
        @NotBlank(message = "El cliente es obligatorio.")
        String cliente,
        String telefonoCliente,
        @NotBlank(message = "La descripcion de los articulos es obligatoria.")
        String descripcionArticulos,
        @NotNull(message = "El valor total es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor total debe ser mayor a cero.")
        BigDecimal valorTotal,
        @NotNull(message = "El abono inicial es obligatorio.")
        @DecimalMin(value = "20000.00", message = "El abono inicial minimo es de 20.000 COP.")
        BigDecimal abonoInicial,
        String observacion
) {
}
