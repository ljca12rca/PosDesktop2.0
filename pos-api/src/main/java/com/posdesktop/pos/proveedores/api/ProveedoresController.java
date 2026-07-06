package com.posdesktop.pos.proveedores.api;

import com.posdesktop.pos.proveedores.api.dto.RegistrarProveedorRequest;
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
@RequestMapping(ApiPaths.PROVEEDORES)
public class ProveedoresController {

    private final ModuleCatalogService moduleCatalogService;

    public ProveedoresController(ModuleCatalogService moduleCatalogService) {
        this.moduleCatalogService = moduleCatalogService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de proveedores disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("proveedores")
        );
    }

    @PostMapping
    public ApiResponse<Void> registrarProveedor(
            @Valid @RequestBody RegistrarProveedorRequest request,
            HttpServletRequest httpRequest
    ) {
        throw new FeaturePendingException(
                "Registrar proveedor aun no tiene logica implementada. Estructura de entrada ya disponible."
        );
    }
}
