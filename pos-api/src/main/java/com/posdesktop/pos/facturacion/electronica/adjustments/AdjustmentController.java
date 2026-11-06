package com.posdesktop.pos.facturacion.electronica.adjustments;
import com.posdesktop.pos.auth.service.PermisosSistema;
import com.posdesktop.pos.auth.web.RequiresPermissions;
import com.posdesktop.pos.shared.api.*;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalSignatureService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.API_V1+"/ajustes")
public class AdjustmentController {
    private final AdjustmentService service;private final AdjustmentSourceResolver sources;
    public AdjustmentController(AdjustmentService service,AdjustmentSourceResolver sources){this.service=service;this.sources=sources;}
    @GetMapping("/facturas") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<List<AdjustmentSourceResolver.Candidate>> search(@RequestParam String buscar,HttpServletRequest r){return ApiResponse.success("Facturas consultadas.",r.getRequestURI(),sources.search(buscar));}
    @GetMapping("/facturas/{provider}/{id}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<AdjustmentService.Source> source(@PathVariable String provider,@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success("Detalle original consultado.",r.getRequestURI(),service.source(provider,id));}
    @PostMapping("/revisar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<AdjustmentTransactions.Quote> quote(@RequestBody AdjustmentTransactions.Request body,HttpServletRequest r){return ApiResponse.success("Ajuste calculado sin guardar.",r.getRequestURI(),service.quote(body));}
    @PostMapping @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<AdjustmentService.Summary> create(@RequestBody AdjustmentTransactions.Request body,HttpServletRequest r){return ApiResponse.success("Ajuste guardado sin envio.",r.getRequestURI(),service.create(body));}
    @GetMapping @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<List<AdjustmentService.Summary>> history(HttpServletRequest r){return ApiResponse.success("Ultimos 100 ajustes.",r.getRequestURI(),service.history());}
    @PostMapping("/{id}/enviar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<AdjustmentService.Summary> send(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success("Resultado del intento.",r.getRequestURI(),service.send(id,false));}
    @PostMapping("/{id}/consultar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<AdjustmentService.Summary> consult(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success("Resultado de la consulta.",r.getRequestURI(),service.send(id,true));}
    @PostMapping("/{id}/descartar") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<AdjustmentService.Summary> discard(@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success("Borrador descartado; evidencia conservada.",r.getRequestURI(),service.discard(id));}
    @GetMapping("/numeracion/{provider}/{id}") @RequiresPermissions(PermisosSistema.VENTAS_VIEW)
    public ApiResponse<AdjustmentTransactions.Numbering> numbering(@PathVariable String provider,@PathVariable UUID id,HttpServletRequest r){return ApiResponse.success("Numeracion consultada.",r.getRequestURI(),service.numbering(provider,id));}
    @PutMapping("/numeracion/{provider}/{id}") @RequiresPermissions(PermisosSistema.VENTAS_EDIT)
    public ApiResponse<AdjustmentTransactions.Numbering> configure(@PathVariable String provider,@PathVariable UUID id,@RequestBody AdjustmentTransactions.Configure body,HttpServletRequest r){
        var session=(com.posdesktop.pos.auth.service.AuthSessionData)r.getAttribute(com.posdesktop.pos.auth.service.AuthService.REQUEST_CONTEXT);
        if(session==null||session.roles()==null||!session.roles().contains("ADMIN"))throw new com.posdesktop.pos.auth.exception.ForbiddenException("Solo un administrador puede configurar la numeracion.");
        return ApiResponse.success("Numeracion guardada.",r.getRequestURI(),service.configure(provider,id,body));}
}
