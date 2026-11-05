package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.*;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.shared.api.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.API_V1 + "/facturacion")
public class FiscalSettingsController {
    private final FiscalSettingsService service;
    public FiscalSettingsController(FiscalSettingsService service) { this.service = service; }
    @GetMapping("/proveedor") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<FiscalSettingsService.ProviderSettings> provider(HttpServletRequest request) {
        return ApiResponse.success("Proveedor consultado.", request.getRequestURI(), service.provider());
    }
    @PutMapping("/proveedor")
    public ApiResponse<FiscalSettingsService.ProviderSettings> saveProvider(@RequestBody FiscalSettingsService.SaveProvider body, HttpServletRequest request) {
        admin(request); return ApiResponse.success("Proveedor guardado.", request.getRequestURI(), service.saveProvider(body));
    }
    @GetMapping("/dian/configuracion/{environment}")
    public ApiResponse<FiscalSettingsService.DianResponse> dian(@PathVariable String environment, HttpServletRequest request) {
        admin(request); return ApiResponse.success("Configuracion DIAN consultada.", request.getRequestURI(), service.dian(environment));
    }
    @PutMapping("/dian/configuracion/{environment}")
    public ApiResponse<FiscalSettingsService.DianResponse> saveDian(@PathVariable String environment,
            @RequestBody FiscalSettingsService.SaveDian body, HttpServletRequest request) {
        admin(request); return ApiResponse.success("Configuracion DIAN guardada.", request.getRequestURI(), service.saveDian(environment, body));
    }
    private static void admin(HttpServletRequest request) {
        var session = (AuthSessionData) request.getAttribute(AuthService.REQUEST_CONTEXT);
        if (session == null || session.roles() == null || !session.roles().contains("ADMIN"))
            throw new ForbiddenException("Solo un administrador puede configurar facturacion electronica.");
    }
}
