package com.posdesktop.pos.facturacion.electronica.dian.transport;

import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.*;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalJson;
import com.posdesktop.pos.invoicing.infrastructure.transport.DianSoapResponseParser;
import com.posdesktop.pos.modelo.relacional.DianSubmission;
import com.posdesktop.pos.shared.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.API_V1+"/facturacion/dian/transporte")
public class DianTransportController {
    private final DianTransportTransactions transactions;private final DianTransportService service;private final DianTransportSettingsService settings;private final FiscalJson json;
    public DianTransportController(DianTransportTransactions transactions,DianTransportService service,DianTransportSettingsService settings,FiscalJson json){this.transactions=transactions;this.service=service;this.settings=settings;this.json=json;}
    public record Summary(UUID id,UUID documentId,String mode,String state,String number,String zipName,String trackId,String message,boolean canSend,boolean canQuery,boolean canCancel,LocalDateTime updatedAt){ }
    public record Exchange(String action,Integer httpStatus,String diagnosis,String responseXml,LocalDateTime createdAt){ }
    public record Detail(Summary summary,DianSoapResponseParser.Reply result,List<Exchange> exchanges){ }
    @GetMapping("/configuracion") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<DianTransportSettingsService.Settings> settings(HttpServletRequest r){return ok(r,settings.get());}
    @PutMapping("/configuracion") public ApiResponse<DianTransportSettingsService.Settings> save(@RequestBody DianTransportSettingsService.Save body,HttpServletRequest r){var session=(AuthSessionData)r.getAttribute(AuthService.REQUEST_CONTEXT);if(session==null||session.roles()==null||!session.roles().contains("ADMIN"))throw new ForbiddenException("Solo ADMIN puede cambiar el transporte DIAN.");return ok(r,settings.save(body));}
    @PostMapping("/documentos/{id}") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<Summary> prepare(@PathVariable UUID id,HttpServletRequest r){return ok(r,summary(transactions.prepare(id)));}
    @GetMapping("/documentos/{id}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<List<Summary>> list(@PathVariable UUID id,HttpServletRequest r){return ok(r,transactions.list(id).stream().map(this::summary).toList());}
    @PostMapping("/{id}/enviar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<Summary> send(@PathVariable UUID id,HttpServletRequest r){return ok(r,summary(service.execute(id,false)));}
    @PostMapping("/{id}/consultar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<Summary> query(@PathVariable UUID id,HttpServletRequest r){return ok(r,summary(service.execute(id,true)));}
    @PostMapping("/{id}/descartar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<Summary> cancel(@PathVariable UUID id,HttpServletRequest r){return ok(r,summary(transactions.cancel(id)));}
    @GetMapping("/{id}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<Detail> detail(@PathVariable UUID id,HttpServletRequest r){var s=transactions.get(id);return ok(r,new Detail(summary(s),s.getResponse()==null?null:json.read(s.getResponse(),DianSoapResponseParser.Reply.class),transactions.history(id).stream().map(e->new Exchange(e.getAction(),e.getHttpStatus(),e.getDiagnosis(),e.getResponseXml(),e.getCreatedAt())).toList()));}
    @GetMapping(value="/{id}/zip",produces="application/zip") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ResponseEntity<byte[]> zip(@PathVariable UUID id){var s=transactions.get(id);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+s.getZipName()+"\"").header(HttpHeaders.CACHE_CONTROL,"no-store").body(s.getZipBytes());}
    private Summary summary(DianSubmission s){boolean available="SIMULACION".equals(s.getMode())||"ONAC_CHAIN_AND_REVOCATION_VALIDATED".equals(s.getSignatureScope());String state="SIMULACION".equals(s.getMode())?"SIMULACION_"+("ACEPTADO".equals(s.getState())?"VALIDADO":s.getState()):"DIAN_"+s.getState();return new Summary(s.getId(),s.getDocumentId(),s.getMode(),state,s.getNumber(),s.getZipName(),s.getTrackId(),s.getMessage(),available&&"PREPARADO".equals(s.getState()),available&&!Set.of("PREPARADO","ACEPTADO","RECHAZADO","CANCELADO").contains(s.getState()),"PREPARADO".equals(s.getState()),s.getUpdatedAt());}
    private static <T> ApiResponse<T> ok(HttpServletRequest r,T data){return ApiResponse.success("Transporte DIAN consultado.",r.getRequestURI(),data);}
}
