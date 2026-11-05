package com.posdesktop.pos.ventas.service;

import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoApiException;
import com.posdesktop.pos.ventas.api.dto.ResultadoFacturaElectronicaVentaResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

/** Orquesta evidencia duradera y reutiliza el proveedor Dataico sin reconstruir su JSON. */
@Service
public class FacturacionVentaService {
    private static final Pattern NEXT = Pattern.compile("(?i)siguiente n(?:ú|u)mero ['‘]([^'’]+)['’]");
    private final FiscalEmissionTransactions transactions;
    private final DataicoFiscalSender sender;
    public FacturacionVentaService(FiscalEmissionTransactions transactions, DataicoFiscalSender sender) {
        this.transactions = transactions; this.sender = sender;
    }
    public ResultadoFacturaElectronicaVentaResponse emitir(UUID saleId, UUID customerId) {
        return send(transactions.prepare(saleId,customerId,false));
    }
    public ResultadoFacturaElectronicaVentaResponse reintentar(UUID saleId, UUID customerId) {
        return send(transactions.prepare(saleId,customerId,true));
    }
    public ResultadoFacturaElectronicaVentaResponse consultarIntento(UUID saleId) {
        var prepared = transactions.forConsult(saleId);
        if (prepared.replay() != null) return prepared.replay();
        try { return transactions.complete(prepared,sender.consult(prepared.payload())); }
        catch (RuntimeException error) { markFailed(prepared,false); throw error; }
    }
    private ResultadoFacturaElectronicaVentaResponse send(FiscalEmissionTransactions.Prepared original) {
        if (original.replay() != null) return original.replay();
        var prepared = original;
        try {
            com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento result;
            try { result = sender.send(prepared.payload()); }
            catch (DataicoApiException error) {
                Long suggested = suggestedNumber(error,prepared.payload().document().prefijo());
                if (suggested == null || Long.toString(suggested).equals(prepared.payload().document().numero())) throw error;
                prepared = transactions.renumber(prepared,suggested);
                result = sender.send(prepared.payload());
            }
            return transactions.complete(prepared,result);
        } catch (RuntimeException error) { markFailed(prepared,definitive(error)); throw error; }
    }
    private void markFailed(FiscalEmissionTransactions.Prepared prepared, boolean definitive) {
        try { transactions.fail(prepared,definitive); }
        catch (RuntimeException persistenceError) {
            // Si tampoco se confirma el cierre del intento, EN_CURSO sigue bloqueando el reenvio.
            org.slf4j.LoggerFactory.getLogger(getClass()).error("No se pudo cerrar el intento fiscal de la venta {}. Revisar su estado antes de reenviar.",prepared.saleId());
        }
    }
    private static boolean definitive(RuntimeException error) {
        if (error instanceof FiscalPreflightException) return true;
        if (error instanceof FiscalConflictException) return false;
        if (!(error instanceof DataicoApiException) || !(error.getCause() instanceof RestClientResponseException http)) return false;
        return java.util.Set.of(400,401,403,404,422).contains(http.getStatusCode().value());
    }
    private static Long suggestedNumber(DataicoApiException error, String prefix) {
        if (!(error.getCause() instanceof RestClientResponseException)) return null;
        var matcher = NEXT.matcher(error.getMessage() == null ? "" : error.getMessage());
        if (!matcher.find()) return null;
        String number = matcher.group(1);
        if (!number.startsWith(prefix) || !number.substring(prefix.length()).matches("[0-9]+")) return null;
        try { return Long.valueOf(number.substring(prefix.length())); }
        catch (NumberFormatException invalid) { return null; }
    }
}
