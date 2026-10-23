package com.posdesktop.pos.ventas.api;

import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.service.ModuleCatalogService;
import com.posdesktop.pos.ventas.api.dto.MovimientoVentaResponse;
import com.posdesktop.pos.ventas.api.dto.EmitirFacturaElectronicaRequest;
import com.posdesktop.pos.ventas.api.dto.RegistrarVentaManualRequest;
import com.posdesktop.pos.ventas.api.dto.ResultadoFacturaElectronicaVentaResponse;
import com.posdesktop.pos.ventas.api.dto.FacturaElectronicaListadoResponse;
import com.posdesktop.pos.ventas.service.FacturasElectronicasConsultaService;
import com.posdesktop.pos.ventas.service.FacturasElectronicasAccionesService;
import com.posdesktop.pos.ventas.api.dto.EnviarCorreoFacturaRequest;
import com.posdesktop.pos.ventas.api.dto.VentaRegistradaResponse;
import com.posdesktop.pos.ventas.service.FacturacionVentaService;
import com.posdesktop.pos.ventas.service.VentasService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.VENTAS)
public class VentasController {

    private final ModuleCatalogService moduleCatalogService;
    private final VentasService ventasService;
    private final FacturacionVentaService facturacionVentaService;
    private final FacturasElectronicasConsultaService facturasElectronicasConsultaService;
    private final FacturasElectronicasAccionesService facturasElectronicasAccionesService;

    public VentasController(
            ModuleCatalogService moduleCatalogService,
            VentasService ventasService,
            FacturacionVentaService facturacionVentaService,
            FacturasElectronicasConsultaService facturasElectronicasConsultaService,
            FacturasElectronicasAccionesService facturasElectronicasAccionesService
    ) {
        this.moduleCatalogService = moduleCatalogService;
        this.ventasService = ventasService;
        this.facturacionVentaService = facturacionVentaService;
        this.facturasElectronicasConsultaService = facturasElectronicasConsultaService;
        this.facturasElectronicasAccionesService = facturasElectronicasAccionesService;
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
    @RequiresPermissions(PermisosSistema.MOVIMIENTOS_VIEW)
    public ApiResponse<List<MovimientoVentaResponse>> movimientos(
            @RequestParam(name = "fechaInicial", required = false) LocalDate fechaInicial,
            @RequestParam(name = "fechaFinal", required = false) LocalDate fechaFinal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Movimientos de ventas consultados correctamente.",
                request.getRequestURI(),
                ventasService.listarMovimientos(fechaInicial, fechaFinal)
        );
    }

    @GetMapping("/{ventaId}")
    @RequiresPermissions(PermisosSistema.MOVIMIENTOS_VIEW)
    public ApiResponse<VentaRegistradaResponse> consultarVenta(
            @PathVariable UUID ventaId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Movimiento consultado correctamente.",
                request.getRequestURI(),
                ventasService.consultarVenta(ventaId)
        );
    }

    @PostMapping
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
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

    @GetMapping("/facturas-electronicas")
    @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<List<FacturaElectronicaListadoResponse>> listarFacturasElectronicas(
            @RequestParam(required = false) String numeracion,
            @RequestParam(required = false) String factura,
            @RequestParam(required = false) LocalDate fechaInicial,
            @RequestParam(required = false) LocalDate fechaFinal,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String estadoDian,
            @RequestParam(required = false) String estadoCliente,
            @RequestParam(required = false) String estadoCorreo,
            HttpServletRequest request
    ) {
        return ApiResponse.success("Facturas electrónicas consultadas correctamente.", request.getRequestURI(),
                facturasElectronicasConsultaService.listar(numeracion, factura, fechaInicial, fechaFinal,
                        cliente, estadoDian, estadoCliente, estadoCorreo));
    }

    @PostMapping("/{ventaId}/factura-electronica")
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<ResultadoFacturaElectronicaVentaResponse> emitirFacturaElectronica(
            @PathVariable UUID ventaId,
            @Valid @RequestBody EmitirFacturaElectronicaRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Factura electrónica enviada a Dataico.",
                httpRequest.getRequestURI(),
                facturacionVentaService.emitir(ventaId, request.clienteId())
        );
    }

    @PostMapping("/facturas-electronicas/{facturaId}/dian")
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<FacturaElectronicaListadoResponse> enviarDian(
            @PathVariable UUID facturaId, HttpServletRequest request) {
        return ApiResponse.success("Solicitud de envío a DIAN procesada.", request.getRequestURI(),
                facturasElectronicasAccionesService.enviarDian(facturaId));
    }

    @PostMapping("/facturas-electronicas/{facturaId}/correo")
    @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<FacturaElectronicaListadoResponse> enviarCorreo(
            @PathVariable UUID facturaId, @Valid @RequestBody EnviarCorreoFacturaRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("Solicitud de envío de correo procesada.", request.getRequestURI(),
                facturasElectronicasAccionesService.enviarCorreo(facturaId, body.email()));
    }
}
