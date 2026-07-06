package com.posdesktop.pos.ventas.api;

import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import com.posdesktop.pos.ventas.api.dto.MovimientoVentaResponse;
import com.posdesktop.pos.ventas.api.dto.RegistrarVentaManualRequest;
import com.posdesktop.pos.ventas.api.dto.VentaRegistradaResponse;
import com.posdesktop.pos.ventas.service.VentasService;
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
@RequestMapping(ApiPaths.VENTAS)
public class VentasController {

    private final ModuleCatalogService moduleCatalogService;
    private final VentasService ventasService;

    public VentasController(ModuleCatalogService moduleCatalogService, VentasService ventasService) {
        this.moduleCatalogService = moduleCatalogService;
        this.ventasService = ventasService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de ventas disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("ventas")
        );
    }

    @GetMapping("/movimientos")
    public ApiResponse<List<MovimientoVentaResponse>> movimientos(
            @RequestParam(required = false) LocalDate fechaInicial,
            @RequestParam(required = false) LocalDate fechaFinal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Movimientos de ventas consultados correctamente.",
                request.getRequestURI(),
                ventasService.listarMovimientos(fechaInicial, fechaFinal)
        );
    }

    @PostMapping
    public ApiResponse<VentaRegistradaResponse> registrarVentaManual(
            @Valid @RequestBody RegistrarVentaManualRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Venta manual registrada correctamente.",
                httpRequest.getRequestURI(),
                ventasService.registrarVentaManual(request)
        );
    }
}
