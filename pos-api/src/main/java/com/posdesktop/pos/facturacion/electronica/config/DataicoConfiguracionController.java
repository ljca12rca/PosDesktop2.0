package com.posdesktop.pos.facturacion.electronica.config;

import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.AuthService;
import com.posdesktop.pos.auth.service.AuthSessionData;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.API_V1 + "/configuracion/dataico")
public class DataicoConfiguracionController {
    private final DataicoConfiguracionService service;
    public DataicoConfiguracionController(DataicoConfiguracionService service) { this.service = service; }

    @GetMapping
    public ApiResponse<ConfiguracionDataicoResponse> consultar(HttpServletRequest request) {
        validarAdministrador(request);
        return ApiResponse.success("Configuración consultada.", request.getRequestURI(), service.consultar());
    }

    @PutMapping
    public ApiResponse<ConfiguracionDataicoResponse> guardar(@Valid @RequestBody GuardarConfiguracionDataicoRequest body,
            HttpServletRequest request) {
        validarAdministrador(request);
        return ApiResponse.success("Configuración guardada.", request.getRequestURI(), service.guardar(body));
    }

    private void validarAdministrador(HttpServletRequest request) {
        AuthSessionData session = (AuthSessionData) request.getAttribute(AuthService.REQUEST_CONTEXT);
        if (session == null || session.roles() == null || !session.roles().contains("ADMIN")) {
            throw new ForbiddenException("Solo un administrador puede configurar la facturación electrónica.");
        }
    }
}
