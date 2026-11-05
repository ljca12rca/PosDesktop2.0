package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.shared.api.*;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.API_V1 + "/facturacion")
public class FiscalDocumentsController {
    private final FiscalDraftService drafts;
    private final FiscalDocumentRepository documents;
    private final FiscalOperationRepository operations;
    private final FiscalEventRepository events;
    private final com.posdesktop.pos.ventas.service.VentasService sales;
    private final VentaRepositorio saleRepository;
    private final FiscalSignatureService signatures;
    public FiscalDocumentsController(FiscalDraftService drafts,FiscalDocumentRepository documents,FiscalOperationRepository operations,FiscalEventRepository events,
            com.posdesktop.pos.ventas.service.VentasService sales,VentaRepositorio saleRepository,FiscalSignatureService signatures) {
        this.drafts=drafts; this.documents=documents; this.operations=operations; this.events=events; this.sales=sales; this.saleRepository=saleRepository;this.signatures=signatures;
    }
    public record Summary(UUID id,UUID saleId,UUID customerId,String provider,String purpose,String environment,String number,String customer,
            BigDecimal total,String state,LocalDateTime createdAt) { }
    public record Event(String type,String detail,LocalDateTime createdAt) { }
    public record Detail(Summary summary,String format,String snapshot,String xml,String hash,List<Event> events) { }
    @PostMapping("/documentos/{id}/firma-local") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<FiscalSignatureService.Result> sign(@PathVariable UUID id,HttpServletRequest request) {
        return ApiResponse.success("Firma local validada. Sin envio a DIAN.",request.getRequestURI(),signatures.sign(id));
    }
    @GetMapping("/documentos/{id}/firma-local/validacion") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<com.posdesktop.pos.invoicing.infrastructure.signature.DianXadesSignature.Validation> verify(@PathVariable UUID id,HttpServletRequest request) {
        return ApiResponse.success("Verificacion criptografica local.",request.getRequestURI(),signatures.verify(id));
    }
    @GetMapping("/ventas/por-numero") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<com.posdesktop.pos.ventas.api.dto.VentaRegistradaResponse> saleByNumber(@RequestParam String numero,HttpServletRequest request) {
        if(numero.isBlank()||numero.length()>40)throw new IllegalArgumentException("Ingresa el numero del comprobante local.");
        var sale=saleRepository.findByNumeroVenta(numero.trim()).orElseThrow(()->new ResourceNotFoundException("No existe una venta con ese numero local."));
        return ApiResponse.success("Venta consultada para previsualizacion.",request.getRequestURI(),sales.consultarVenta(sale.getId()));
    }
    @GetMapping("/ventas/{saleId}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<com.posdesktop.pos.ventas.api.dto.VentaRegistradaResponse> sale(@PathVariable UUID saleId,HttpServletRequest request) {
        return ApiResponse.success("Venta consultada para previsualizacion.",request.getRequestURI(),sales.consultarVenta(saleId));
    }
    @GetMapping("/dian/preparacion/{environment}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<FiscalDraftService.Preparation> preparation(@PathVariable String environment,HttpServletRequest request) {
        return ApiResponse.success("Preparacion consultada.",request.getRequestURI(),drafts.preparation(environment));
    }
    @PostMapping("/ventas/{saleId}/borrador") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<FiscalDraftService.Result> generate(@PathVariable UUID saleId,@Valid @RequestBody FiscalDraftService.Request body,HttpServletRequest request) {
        return ApiResponse.success("Borrador guardado sin firma ni envio.",request.getRequestURI(),drafts.generate(saleId,body));
    }
    @GetMapping("/documentos") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<List<Summary>> documents(HttpServletRequest request) {
        return ApiResponse.success("Ultimos 200 documentos fiscales.",request.getRequestURI(),documents.findTop200ByOrderByCreadoEnDesc().stream().map(this::summary).toList());
    }
    @GetMapping("/documentos/{id}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<Detail> detail(@PathVariable UUID id,HttpServletRequest request) {
        var doc = documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("No existe el documento fiscal."));
        var history = events.findByDocumentoIdOrderByCreadoEnAsc(id).stream().map(e -> new Event(e.getTipo(),e.getDetalle(),e.getCreadoEn())).toList();
        return ApiResponse.success("Evidencia fiscal consultada.",request.getRequestURI(),new Detail(summary(doc),doc.getFormato(),doc.getSnapshotJson(),doc.getXml(),doc.getHuella(),history));
    }
    private Summary summary(FiscalDocumentRecord doc) {
        String state = "BORRADOR".equals(doc.getProposito()) ? (FiscalSignatureService.FORMAT.equals(doc.getFormato()) ? "FIRMADO_LOCAL" : "BORRADOR_SIN_FIRMA") : operations.findById(doc.getVentaId())
                .map(o -> doc.getId().equals(o.getDocumentoId()) ? o.getEstado() : "NUMERO_RECHAZADO").orElse("SIN_CONFIRMACION");
        return new Summary(doc.getId(),doc.getVentaId(),doc.getClienteId(),doc.getProveedor(),doc.getProposito(),doc.getAmbiente(),doc.getNumero(),doc.getClienteNombre(),doc.getTotal(),state,doc.getCreadoEn());
    }
}
