package com.posdesktop.pos.system.api;

import com.posdesktop.pos.auth.web.PublicEndpoint;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.api.SystemStatusResponse;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.SYSTEM)
public class SystemController {

    private final ModuleCatalogService moduleCatalogService;

    public SystemController(ModuleCatalogService moduleCatalogService) {
        this.moduleCatalogService = moduleCatalogService;
    }

    @PublicEndpoint
    @GetMapping("/ping")
    public ApiResponse<SystemStatusResponse> ping(HttpServletRequest request) {
        return ApiResponse.success(
                "API disponible para iniciar desarrollo de logica.",
                request.getRequestURI(),
                moduleCatalogService.systemStatus()
        );
    }

    @GetMapping("/modules")
    public ApiResponse<List<ModuleStatusResponse>> modules(HttpServletRequest request) {
        return ApiResponse.success(
                "Catalogo inicial de modulos disponible.",
                request.getRequestURI(),
                moduleCatalogService.availableModules()
        );
    }

    @GetMapping("/modules/{code}")
    public ApiResponse<ModuleStatusResponse> module(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Modulo consultado correctamente.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode(code)
        );
    }
}
