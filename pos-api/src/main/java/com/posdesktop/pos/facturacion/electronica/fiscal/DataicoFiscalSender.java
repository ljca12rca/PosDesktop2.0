package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.config.*;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DataicoFiscalSender {
    private final FacturacionElectronicaService provider;
    private final DataicoConfiguracionService settings;
    public DataicoFiscalSender(FacturacionElectronicaService provider, DataicoConfiguracionService settings) { this.provider = provider; this.settings = settings; }
    /** Conserva cuenta/conexion mientras se usa el proveedor legado; el intento ya fue confirmado localmente. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoEmisionDocumento send(DataicoSalePayload payload) {
        var current=checkAccount(payload);
        var json=new FiscalJson();
        String mapped=json.write(new com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoInvoiceMapper(current).toRequest(payload.document()));
        if(payload.providerRequestJson()==null || !json.equivalent(mapped,payload.providerRequestJson()))
            throw new FiscalPreflightException("El adaptador ya no reproduce la solicitud original. Revisa la evidencia antes de reintentar.");
        return provider.emitir("dataico", payload.document());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoEmisionDocumento consult(DataicoSalePayload payload) {
        checkAccount(payload); return provider.consultarFactura("dataico",payload.fullNumber());
    }
    private DataicoProperties checkAccount(DataicoSalePayload payload) {
        DataicoProperties current = settings.paraEmision();
        validate(current);
        if (!Objects.equals(current.accountId(),payload.account()) || !Objects.equals(current.baseUrl(),payload.baseUrl()))
            throw new FiscalPreflightException("La cuenta o conexion Dataico cambio. Restaura la cuenta del documento antes de continuar.");
        return current;
    }
    static void validate(DataicoProperties current) {
        if (!current.enabled()) throw new FiscalPreflightException("Dataico esta deshabilitado. Revisa su configuracion.");
        if (current.tokenParaProveedor() == null || current.tokenParaProveedor().isBlank())
            throw new FiscalPreflightException("Configura el token Dataico antes de emitir.");
        try { DataicoConfiguracionService.validarUrl(current.baseUrl()==null||current.baseUrl().isBlank()?DataicoProperties.DEFAULT_BASE_URL:current.baseUrl()); }
        catch(IllegalArgumentException error) { throw new FiscalPreflightException("Revisa la URL oficial HTTPS en Configuracion Dataico."); }
    }
}
