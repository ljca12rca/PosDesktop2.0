package com.posdesktop.pos.articulos.api;

import com.posdesktop.pos.articulos.api.dto.RegistrarArticuloRequest;
import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.exception.FeaturePendingException;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ARTICULOS)
public class ArticulosController {

    private final ModuleCatalogService moduleCatalogService;

    public ArticulosController(ModuleCatalogService moduleCatalogService) {
        this.moduleCatalogService = moduleCatalogService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de articulos disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("articulos")
        );
    }

    @PostMapping
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<Void> registrarArticulo(
            @Valid @RequestBody RegistrarArticuloRequest request,
            HttpServletRequest httpRequest
    ) {
        throw new FeaturePendingException(
                "Registrar articulo aun no tiene logica implementada. Base lista para integrar catalogo al POS."
        );
    }
}
