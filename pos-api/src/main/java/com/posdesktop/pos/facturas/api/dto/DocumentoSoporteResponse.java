package com.posdesktop.pos.facturas.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentoSoporteResponse(
        String id,
        String entidadOrigen,
        String entidadOrigenId,
        String tipoDocumento,
        String nombreArchivo,
        String contentType,
        Long tamanioBytes,
        String rutaArchivo,
        String rutaRelativa,
        List<String> carpetas,
        String checksum,
        String observacion,
        LocalDateTime cargadoEn
) {
}
