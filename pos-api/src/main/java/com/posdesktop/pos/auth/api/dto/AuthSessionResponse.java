package com.posdesktop.pos.auth.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuthSessionResponse(
        String token,
        LocalDateTime expiraEn,
        String usuarioId,
        String username,
        String nombreCompleto,
        List<String> roles,
        List<String> permisos
) {
}
