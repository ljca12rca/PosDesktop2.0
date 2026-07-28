package com.posdesktop.pos.facturas.api;

import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.facturas.api.dto.ActualizarFacturaProveedorRequest;
import com.posdesktop.pos.facturas.api.dto.FacturaProveedorDetalleResponse;
import com.posdesktop.pos.facturas.api.dto.FacturaProveedorListadoResponse;
import com.posdesktop.pos.facturas.api.dto.RegistrarFacturaProveedorRequest;
import com.posdesktop.pos.facturas.api.dto.RegistrarPagoFacturaRequest;
import com.posdesktop.pos.facturas.service.FacturasProveedorService;
import com.posdesktop.pos.modelo.enumeraciones.EstadoFacturaProveedor;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiPaths.FACTURAS)
public class FacturasProveedorController {

    private final ModuleCatalogService moduleCatalogService;
    private final FacturasProveedorService facturasProveedorService;

    public FacturasProveedorController(
            ModuleCatalogService moduleCatalogService,
            FacturasProveedorService facturasProveedorService
    ) {
        this.moduleCatalogService = moduleCatalogService;
        this.facturasProveedorService = facturasProveedorService;
    }

    @GetMapping("/estado")
    public ApiResponse<ModuleStatusResponse> estado(HttpServletRequest request) {
        return ApiResponse.success(
                "Modulo de facturas proveedor disponible.",
                request.getRequestURI(),
                moduleCatalogService.moduleByCode("facturas-proveedor")
        );
    }

    @GetMapping
    @RequiresPermissions(PermisosSistema.FACTURAS_VIEW)
    public ApiResponse<List<FacturaProveedorListadoResponse>> listarFacturas(
            @RequestParam(name = "proveedorId", required = false) UUID proveedorId,
            @RequestParam(name = "estado", required = false) EstadoFacturaProveedor estado,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Facturas consultadas correctamente.",
                request.getRequestURI(),
                facturasProveedorService.listarFacturas(proveedorId, estado)
        );
    }

    @GetMapping("/{facturaId}")
    @RequiresPermissions(PermisosSistema.FACTURAS_VIEW)
    public ApiResponse<FacturaProveedorDetalleResponse> consultarFactura(
            @PathVariable UUID facturaId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                "Factura consultada correctamente.",
                request.getRequestURI(),
                facturasProveedorService.consultarFactura(facturaId)
        );
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresPermissions(PermisosSistema.FACTURAS_EDIT)
    public ApiResponse<FacturaProveedorDetalleResponse> registrarFacturaProveedor(
            @Valid @RequestBody RegistrarFacturaProveedorRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Factura proveedor registrada correctamente.",
                httpRequest.getRequestURI(),
                facturasProveedorService.registrarFactura(request, List.of())
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermissions(PermisosSistema.FACTURAS_EDIT)
    public ApiResponse<FacturaProveedorDetalleResponse> registrarFacturaProveedorConImagenes(
            @Valid @RequestPart("factura") RegistrarFacturaProveedorRequest request,
            @RequestPart(name = "imagenes", required = false) List<MultipartFile> imagenes,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Factura proveedor registrada correctamente.",
                httpRequest.getRequestURI(),
                facturasProveedorService.registrarFactura(request, imagenes)
        );
    }

    @PutMapping(path = "/{facturaId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresPermissions(PermisosSistema.FACTURAS_EDIT)
    public ApiResponse<FacturaProveedorDetalleResponse> actualizarFacturaProveedor(
            @PathVariable UUID facturaId,
            @Valid @RequestBody ActualizarFacturaProveedorRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Factura proveedor actualizada correctamente.",
                httpRequest.getRequestURI(),
                facturasProveedorService.actualizarFactura(facturaId, request)
        );
    }

    @PostMapping(path = "/{facturaId}/abonos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresPermissions(PermisosSistema.FACTURAS_EDIT)
    public ApiResponse<FacturaProveedorDetalleResponse> registrarAbono(
            @PathVariable UUID facturaId,
            @Valid @RequestBody RegistrarPagoFacturaRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Abono registrado correctamente.",
                httpRequest.getRequestURI(),
                facturasProveedorService.registrarAbono(facturaId, request, List.of())
        );
    }

    @PostMapping(path = "/{facturaId}/abonos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermissions(PermisosSistema.FACTURAS_EDIT)
    public ApiResponse<FacturaProveedorDetalleResponse> registrarAbonoConSoporte(
            @PathVariable UUID facturaId,
            @Valid @RequestPart("abono") RegistrarPagoFacturaRequest request,
            @RequestPart(name = "soportes", required = false) List<MultipartFile> soportes,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Abono registrado correctamente.",
                httpRequest.getRequestURI(),
                facturasProveedorService.registrarAbono(facturaId, request, soportes)
        );
    }
}
