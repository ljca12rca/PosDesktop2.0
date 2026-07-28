package com.posdesktop.pos.cierres.api;

import com.posdesktop.pos.auth.service.AuthService;
import com.posdesktop.pos.auth.service.AuthSessionData;
import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.cierres.api.dto.CierreDiarioListadoResponse;
import com.posdesktop.pos.cierres.api.dto.RegistrarCierreRequest;
import com.posdesktop.pos.cierres.api.dto.ResumenCierreDiarioResponse;
import com.posdesktop.pos.cierres.service.CierresService;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.CIERRES)
public class CierresController {

    private final ModuleCatalogService moduleCatalogService;
    private final CierresService cierresService;

    public CierresController(ModuleCatalogService moduleCatalogService, CierresService cierresService) {
        this.moduleCatalogService = moduleCatalogService;
        this.cierresService = cierresService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de cierres disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("cierres")
        );
    }

    @GetMapping("/resumen")
    @RequiresPermissions(PermisosSistema.CIERRES_VIEW)
    public ApiResponse<ResumenCierreDiarioResponse> resumen(
            @RequestParam(name = "fecha", required = false) LocalDate fecha,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Resumen diario consultado correctamente.",
                request.getRequestURI(),
                cierresService.consultarResumen(fecha)
        );
    }

    @GetMapping
    @RequiresPermissions(PermisosSistema.CIERRES_VIEW)
    public ApiResponse<List<CierreDiarioListadoResponse>> listar(
            @RequestParam(name = "fechaInicial", required = false) LocalDate fechaInicial,
            @RequestParam(name = "fechaFinal", required = false) LocalDate fechaFinal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Historial de cierres consultado correctamente.",
                request.getRequestURI(),
                cierresService.listarCierres(fechaInicial, fechaFinal)
        );
    }

    @PostMapping
    @RequiresPermissions(PermisosSistema.CIERRES_EDIT)
    public ApiResponse<ResumenCierreDiarioResponse> registrarCierre(
            @Valid @RequestBody RegistrarCierreRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthSessionData session = (AuthSessionData) httpRequest.getAttribute(AuthService.REQUEST_CONTEXT);
        return ApiResponse.success(
                "Cierre diario registrado correctamente.",
                httpRequest.getRequestURI(),
                cierresService.registrarCierre(request, session)
        );
    }
}
