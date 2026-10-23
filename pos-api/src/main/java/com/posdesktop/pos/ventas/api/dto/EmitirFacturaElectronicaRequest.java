package com.posdesktop.pos.ventas.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmitirFacturaElectronicaRequest(@NotNull UUID clienteId) {
}
