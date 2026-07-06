package com.posdesktop.pos.separados.api;

import com.posdesktop.pos.separados.api.dto.RegistrarSeparadoRequest;
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
@RequestMapping(ApiPaths.SEPARADOS)
public class SeparadosController {

    private final ModuleCatalogService moduleCatalogService;

    public SeparadosController(ModuleCatalogService moduleCatalogService) {
        this.moduleCatalogService = moduleCatalogService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de separados disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("separados")
        );
    }

    @PostMapping
    public ApiResponse<Void> registrarSeparado(
            @Valid @RequestBody RegistrarSeparadoRequest request,
            HttpServletRequest httpRequest
    ) {
        throw new FeaturePendingException(
                "Registrar separado aun no tiene logica implementada. Regla minima de 20.000 ya esta modelada en el request."
        );
    }
}
