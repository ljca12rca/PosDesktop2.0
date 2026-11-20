package com.posdesktop.pos.facturacion.electronica.delivery;
import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.service.*;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.modelo.relacional.FiscalMailAttempt;
import com.posdesktop.pos.repositorio.relacional.ClienteElectronicoRepositorio;
import com.posdesktop.pos.shared.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.API_V1+"/facturacion/entrega")
public class FiscalDeliveryController {
 private final FiscalDeliverySettingsService settings;private final FiscalDocumentFiles files;private final FiscalMailService service;private final FiscalMailTransactions transactions;private final ClienteElectronicoRepositorio customers;
 public FiscalDeliveryController(FiscalDeliverySettingsService settings,FiscalDocumentFiles files,FiscalMailService service,FiscalMailTransactions transactions,ClienteElectronicoRepositorio customers){this.settings=settings;this.files=files;this.service=service;this.transactions=transactions;this.customers=customers;}
 public record Mail(UUID id,String recipient,String mode,String state,String message,String bundleHash,LocalDateTime createdAt){}
 public record Preparation(String recipient,String mode,List<Mail> history,List<FiscalDocumentFiles.Verification> responses){}
 @GetMapping("/configuracion") public ApiResponse<FiscalDeliverySettingsService.Settings> settings(HttpServletRequest r){admin(r);return ok(r,settings.get());}
 @PutMapping("/configuracion") public ApiResponse<FiscalDeliverySettingsService.Settings> save(@RequestBody FiscalDeliverySettingsService.Save body,HttpServletRequest r){admin(r);return ok(r,settings.save(body));}
 @GetMapping("/documentos/{id}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
 public ApiResponse<Preparation> preparation(@PathVariable UUID id,HttpServletRequest r){var d=files.document(id);String email=d.getClienteId()==null?"":customers.findById(d.getClienteId()).map(c->Objects.toString(c.getEmail(),"")).orElse("");return ok(r,new Preparation(email,settings.get().configuration().mode(),transactions.list(id).stream().map(this::mail).toList(),files.verifyResponses(id)));}
 @GetMapping(value="/documentos/{id}/pdf",produces="application/pdf") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
 public ResponseEntity<byte[]> pdf(@PathVariable UUID id,@RequestParam(defaultValue="A4") FiscalPdfRenderer.Paper paper){return attachment("PRUEBAS-"+FiscalDocumentFiles.safe(files.document(id).getNumero())+".pdf","application/pdf",files.pdf(id,paper));}
 @GetMapping(value="/documentos/{id}/paquete",produces="application/zip") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
 public ResponseEntity<byte[]> bundle(@PathVariable UUID id,@RequestParam(defaultValue="A4") FiscalPdfRenderer.Paper paper){return attachment("PRUEBAS-"+FiscalDocumentFiles.safe(files.document(id).getNumero())+".zip","application/zip",files.bundle(id,paper));}
 @PostMapping("/documentos/{id}/correo") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
 public ApiResponse<Mail> send(@PathVariable UUID id,@RequestBody FiscalMailService.Request body,HttpServletRequest r){return ok(r,mail(service.send(id,body)));}
 @GetMapping(value="/correos/{id}/paquete",produces="application/zip") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
 public ResponseEntity<byte[]> sentBundle(@PathVariable UUID id){return attachment("PRUEBAS-entrega-"+id+".zip","application/zip",transactions.get(id).getBundle());}
 private Mail mail(FiscalMailAttempt a){return new Mail(a.getId(),a.getRecipient(),a.getMode(),a.getState(),a.getMessage(),a.getBundleHash(),a.getCreatedAt());}
 private static ResponseEntity<byte[]> attachment(String name,String type,byte[] bytes){return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE,type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);}
 private static <T> ApiResponse<T> ok(HttpServletRequest r,T data){return ApiResponse.success("Entrega fiscal consultada.",r.getRequestURI(),data);}
 private static void admin(HttpServletRequest r){var session=(AuthSessionData)r.getAttribute(AuthService.REQUEST_CONTEXT);if(session==null||session.roles()==null||!session.roles().contains("ADMIN"))throw new ForbiddenException("Solo ADMIN puede configurar SMTP y certificados de respuesta.");}
}
