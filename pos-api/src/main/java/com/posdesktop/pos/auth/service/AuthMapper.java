package com.posdesktop.pos.auth.service;

import com.posdesktop.pos.auth.api.dto.AuthSessionResponse;
import java.util.ArrayList;

public final class AuthMapper {

    private AuthMapper() {
    }

    public static AuthSessionResponse toResponse(AuthSessionData data) {
        return new AuthSessionResponse(
                data.token(),
                data.expiraEn(),
                data.usuarioId(),
                data.username(),
                data.nombreCompleto(),
                new ArrayList<>(data.roles()),
                new ArrayList<>(data.permisos())
        );
    }
}
