package com.posdesktop.pos.separados.api;

import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.modelo.enumeraciones.EstadoSeparado;
import com.posdesktop.pos.separados.api.dto.RegistrarAbonoSeparadoRequest;
import com.posdesktop.pos.separados.api.dto.RegistrarSeparadoRequest;
import com.posdesktop.pos.separados.api.dto.SeparadoDetalleResponse;
import com.posdesktop.pos.separados.api.dto.SeparadoListadoResponse;
import com.posdesktop.pos.separados.service.SeparadosService;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.SEPARADOS)
public class SeparadosController {

    private final ModuleCatalogService moduleCatalogService;
    private final SeparadosService separadosService;

    public SeparadosController(ModuleCatalogService moduleCatalogService, SeparadosService separadosService) {
        this.moduleCatalogService = moduleCatalogService;
        this.separadosService = separadosService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de separados disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("separados")
        );
    }

    @GetMapping
    @RequiresPermissions(PermisosSistema.SEPARADOS_VIEW)
    public ApiResponse<List<SeparadoListadoResponse>> listarSeparados(
            @RequestParam(name = "estado", required = false) EstadoSeparado estado,
            @RequestParam(name = "articulo", required = false) String articulo,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Separados consultados correctamente.",
                request.getRequestURI(),
                separadosService.listarSeparados(estado, articulo)
        );
    }

    @GetMapping("/{separadoId}")
    @RequiresPermissions(PermisosSistema.SEPARADOS_VIEW)
    public ApiResponse<SeparadoDetalleResponse> consultarSeparado(
            @PathVariable UUID separadoId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Detalle del separado consultado correctamente.",
                request.getRequestURI(),
                separadosService.consultarSeparado(separadoId)
        );
    }

    @PostMapping
    @RequiresPermissions(PermisosSistema.SEPARADOS_EDIT)
    public ApiResponse<SeparadoDetalleResponse> registrarSeparado(
            @Valid @RequestBody RegistrarSeparadoRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Separado registrado correctamente.",
                httpRequest.getRequestURI(),
                separadosService.registrarSeparado(request)
        );
    }

    @PostMapping("/{separadoId}/abonos")
    @RequiresPermissions(PermisosSistema.SEPARADOS_EDIT)
    public ApiResponse<SeparadoDetalleResponse> registrarAbono(
            @PathVariable UUID separadoId,
            @Valid @RequestBody RegistrarAbonoSeparadoRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Abono registrado correctamente.",
                httpRequest.getRequestURI(),
                separadosService.registrarAbono(separadoId, request)
        );
    }
}
