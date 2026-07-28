package com.posdesktop.pos.proveedores.api;

import com.posdesktop.pos.proveedores.api.dto.ProveedorResponse;
import com.posdesktop.pos.proveedores.api.dto.RegistrarProveedorRequest;
import com.posdesktop.pos.proveedores.service.ProveedoresService;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.PROVEEDORES)
public class ProveedoresController {

    private final ModuleCatalogService moduleCatalogService;
    private final ProveedoresService proveedoresService;

    public ProveedoresController(
            ModuleCatalogService moduleCatalogService,
            ProveedoresService proveedoresService
    ) {
        this.moduleCatalogService = moduleCatalogService;
        this.proveedoresService = proveedoresService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de proveedores disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("proveedores")
        );
    }

    @GetMapping
    public ApiResponse<List<ProveedorResponse>> listarProveedores(HttpServletRequest request) {
        return ApiResponse.success(
                "Proveedores consultados correctamente.",
                request.getRequestURI(),
                proveedoresService.listarProveedores()
        );
    }

    @PostMapping
    public ApiResponse<ProveedorResponse> registrarProveedor(
            @Valid @RequestBody RegistrarProveedorRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Proveedor registrado correctamente.",
                httpRequest.getRequestURI(),
                proveedoresService.registrarProveedor(request)
        );
    }
}
