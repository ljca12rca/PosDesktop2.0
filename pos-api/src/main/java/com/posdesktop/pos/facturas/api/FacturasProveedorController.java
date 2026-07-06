package com.posdesktop.pos.facturas.api;

import com.posdesktop.pos.facturas.api.dto.RegistrarFacturaProveedorRequest;
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
@RequestMapping(ApiPaths.FACTURAS)
public class FacturasProveedorController {

    private final ModuleCatalogService moduleCatalogService;

    public FacturasProveedorController(ModuleCatalogService moduleCatalogService) {
        this.moduleCatalogService = moduleCatalogService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de facturas proveedor disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("facturas-proveedor")
        );
    }

    @PostMapping
    public ApiResponse<Void> registrarFacturaProveedor(
            @Valid @RequestBody RegistrarFacturaProveedorRequest request,
            HttpServletRequest httpRequest
    ) {
        throw new FeaturePendingException(
                "Registrar factura proveedor aun no tiene logica implementada. Contrato base listo para deuda y pagos."
        );
    }
}
