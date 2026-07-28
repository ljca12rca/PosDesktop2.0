package com.posdesktop.pos.auth.service;

import java.time.LocalDateTime;
import java.util.Set;

public record AuthSessionData(
        String token,
        LocalDateTime expiraEn,
        String usuarioId,
        String username,
        String nombreCompleto,
        Set<String> roles,
        Set<String> permisos
) {
}
