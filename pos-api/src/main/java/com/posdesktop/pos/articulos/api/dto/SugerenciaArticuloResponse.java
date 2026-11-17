package com.posdesktop.pos.articulos.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SugerenciaArticuloResponse(UUID id, String nombre, BigDecimal precioBase) { }
