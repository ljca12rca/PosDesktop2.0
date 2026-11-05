package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.facturacion.electronica.config.*;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import com.posdesktop.pos.ventas.api.dto.ResultadoFacturaElectronicaVentaResponse;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Transacciones cortas y confirmadas antes del HTTP; no se pierde el numero en un timeout. */
@Service
public class FiscalEmissionTransactions {
    private final VentaRepositorio sales;
    private final ClienteElectronicoRepositorio customers;
    private final FacturaElectronicaRepositorio invoices;
    private final DataicoConsecutivoRepositorio sequences;
    private final DataicoConfiguracionService dataico;
    private final FiscalProviderSettingsRepository providers;
    private final FiscalDocumentRepository documents;
    private final FiscalOperationRepository operations;
    private final FiscalEventRepository events;
    private final FiscalJson json;
    public FiscalEmissionTransactions(VentaRepositorio sales, ClienteElectronicoRepositorio customers, FacturaElectronicaRepositorio invoices,
            DataicoConsecutivoRepositorio sequences, DataicoConfiguracionService dataico, FiscalProviderSettingsRepository providers,
            FiscalDocumentRepository documents, FiscalOperationRepository operations, FiscalEventRepository events, FiscalJson json) {
        this.sales=sales; this.customers=customers; this.invoices=invoices; this.sequences=sequences; this.dataico=dataico;
        this.providers=providers; this.documents=documents; this.operations=operations; this.events=events; this.json=json;
    }
    public record Prepared(UUID saleId, UUID owner, UUID documentId, DataicoSalePayload payload, ResultadoFacturaElectronicaVentaResponse replay) { }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared prepare(UUID saleId, UUID customerId, boolean retry) {
        // Mismo orden en generacion/seleccion: configuracion -> venta -> operacion -> Dataico/secuencia.
        var selection = providers.lockSettings().orElseThrow();
        var sale = sales.findForFiscalUpdate(saleId).orElseThrow(() -> new ResourceNotFoundException("No existe la venta."));
        var operation = operations.lockOperation(saleId).orElse(null);
        if (operation != null) {
            if (!operation.getClienteId().equals(customerId)) throw new FiscalConflictException("Esta venta ya tiene un cliente fiscal asociado.");
            if (!"DATAICO".equals(operation.getProveedor())) throw new FiscalConflictException("Esta venta pertenece a otro proveedor.");
            if ("CONFIRMADO".equals(operation.getEstado()))
                return new Prepared(saleId,null,operation.getDocumentoId(),null,json.read(operation.getResultadoJson(),ResultadoFacturaElectronicaVentaResponse.class));
            if (!retry || !"ERROR_SEGURO".equals(operation.getEstado())) throw new FiscalConflictException(
                    "La factura ya tiene un intento registrado. Consulta su estado antes de reintentar; no se emitira otro documento.");
            var owner = UUID.randomUUID(); operation.retry(owner); operations.saveAndFlush(operation);
            var document = documents.findById(operation.getDocumentoId()).orElseThrow();
            events.save(new FiscalEvent(document.getId(),"REINTENTO_EXPLICITO","Se reutiliza el mismo documento y consecutivo tras rechazo HTTP definitivo."));
            return new Prepared(saleId,owner,document.getId(),json.read(document.getSnapshotJson(),DataicoSalePayload.class),null);
        }
        var legacy = invoices.findByVentaId(saleId).orElse(null);
        if (legacy != null) {
            if (!"DATAICO".equals(legacy.getProveedor())) throw new FiscalConflictException("La venta fue facturada por otro proveedor.");
            return new Prepared(saleId,null,null,null,legacyResult(legacy));
        }
        if (!"DATAICO".equals(selection.getProveedor())) throw new UnsupportedOperationException(
                "DIAN directa solo permite borradores locales. La venta esta guardada; genera su XML sin enviarlo.");
        var customer = customers.findById(customerId).orElseThrow(() -> new ResourceNotFoundException("No existe el cliente electronico."));
        var config = dataico.paraEmision();
        DataicoFiscalSender.validate(config);
        String prefix = config.prefix();
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("Configura el prefijo Dataico antes de emitir.");
        long initial = config.initialNumber() == null || config.initialNumber().isBlank() ? 1 : Long.parseLong(config.initialNumber().replaceAll("\\D",""));
        var sequence = sequences.findByPrefijoForUpdate(prefix).orElseGet(() -> sequences.saveAndFlush(new DataicoConsecutivo(prefix,initial)));
        long number = sequence.getSiguienteNumero(); config.validarConsecutivo(number);
        var payload = DataicoSalePayload.create(sale,customer,Long.toString(number),config);
        var document = saveRequest(payload);
        sequence.setSiguienteNumero(Math.addExact(number,1)); sequences.saveAndFlush(sequence);
        var owner = UUID.randomUUID();
        operations.saveAndFlush(new FiscalOperation(saleId,customerId,"DATAICO",document.getId(),owner));
        events.save(new FiscalEvent(document.getId(),"RESERVADO","Snapshot y consecutivo confirmados antes de contactar al proveedor."));
        return new Prepared(saleId,owner,document.getId(),payload,null);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared renumber(Prepared prepared, long number) {
        var operation = owned(prepared);
        var config = dataico.paraEmision();
        var original = prepared.payload();
        if (!Objects.equals(config.accountId(),original.account()) || !Objects.equals(config.prefix(),original.document().prefijo()))
            throw new FiscalConflictException("La cuenta o numeracion Dataico cambio durante el intento.");
        config.validarConsecutivo(number);
        var sequence = sequences.findByPrefijoForUpdate(config.prefix()).orElseThrow();
        if (number < sequence.getSiguienteNumero()) throw new FiscalConflictException("El numero sugerido ya fue reservado. Consulta el estado del documento.");
        var payload = original.renumber(Long.toString(number)); var document = saveRequest(payload);
        sequence.setSiguienteNumero(Math.addExact(number,1)); sequences.saveAndFlush(sequence);
        operation.changeDocument(document.getId()); operations.saveAndFlush(operation);
        events.save(new FiscalEvent(prepared.documentId(),"NUMERO_RECHAZADO","Dataico rechazo el numero y proporciono un siguiente numero explicito."));
        events.save(new FiscalEvent(document.getId(),"RESERVADO","Nuevo intento con el numero indicado por el proveedor; se conserva el anterior."));
        return new Prepared(prepared.saleId(),prepared.owner(),document.getId(),payload,null);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Prepared prepared, boolean definitive) {
        var operation = owned(prepared);
        operation.changeState(definitive ? "ERROR_SEGURO" : "INCIERTO"); operations.saveAndFlush(operation);
        events.save(new FiscalEvent(prepared.documentId(),definitive ? "RECHAZO_HTTP" : "RESULTADO_INCIERTO",
                definitive ? "Rechazo HTTP definitivo. Un reintento explicito reutilizara el documento." : "No se confirma el resultado remoto. Se bloquea una nueva emision automatica."));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoFacturaElectronicaVentaResponse complete(Prepared prepared, ResultadoEmisionDocumento result) {
        var operation = owned(prepared); var p = prepared.payload();
        if (result.identificadorExterno() == null || result.identificadorExterno().isBlank())
            throw new FiscalConflictException("Dataico no confirmo un identificador de documento. Consulta el intento antes de emitir nuevamente.");
        if (result.numero() != null && !result.numero().equals(p.document().numero()) && !result.numero().equals(p.fullNumber()))
            throw new FiscalConflictException("Dataico respondio con otra numeracion. Es necesaria una conciliacion del intento.");
        FacturaElectronica invoice = new FacturaElectronica();
        invoice.setVentaId(p.saleId()); invoice.setFechaEmision(p.date()); invoice.setNumeracion(p.document().prefijo()); invoice.setNumeroFactura(p.fullNumber());
        invoice.setClienteNombre(p.customerName()); invoice.setClienteIdentificacion(p.document().adquirente().identificacion()); invoice.setClienteEmail(p.email());
        invoice.setImpuestoTotal(p.tax()); invoice.setRetencionTotal(java.math.BigDecimal.ZERO); invoice.setTotal(p.total());
        invoice.setEstadoDian(result.estado() == null ? "ENVIADA" : result.estado()); invoice.setEstadoCliente("REGISTRADO");
        String mail = mailStatus(result.estadoCorreo(),p.document().enviarEmail()); invoice.setEstadoCorreo(mail);
        invoice.setMensajeCorreo(switch(mail) { case "ENVIADO" -> "Dataico confirmo el envio del correo."; case "ERROR" -> "Dataico reporto un error al enviar el correo.";
            case "NO_SOLICITADO" -> "No se solicito envio por correo."; case "NO_ENVIADO" -> "Dataico aun no ha enviado el correo."; default -> "El envio esta pendiente de confirmacion."; });
        invoice.setMensajeDian(limit(result.mensaje(),1000)); invoice.setPdfUrl(limit(result.pdfUrl(),1000)); invoice.setIdentificadorDataico(result.identificadorExterno());
        invoices.saveAndFlush(invoice);
        var response = new ResultadoFacturaElectronicaVentaResponse(result.identificadorExterno(),result.numero(),result.estado(),result.cufe(),result.qrCodeUrl(),result.mensaje(),result.pdfUrl());
        operation.complete(json.write(response)); operations.saveAndFlush(operation);
        events.save(new FiscalEvent(prepared.documentId(),"RESPUESTA_RECIBIDA","Documento registrado por Dataico; el estado DIAN se conserva por separado."));
        return response;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Prepared forConsult(UUID saleId) {
        var operation = operations.lockOperation(saleId).orElseThrow(() -> new ResourceNotFoundException("No existe intento de facturacion para esta venta."));
        if ("CONFIRMADO".equals(operation.getEstado())) return new Prepared(saleId,null,operation.getDocumentoId(),null,json.read(operation.getResultadoJson(),ResultadoFacturaElectronicaVentaResponse.class));
        if ("EN_CURSO".equals(operation.getEstado()) && operation.getActualizadoEn().isAfter(java.time.LocalDateTime.now().minusMinutes(3)))
            throw new FiscalConflictException("El envio sigue en curso. Espera antes de consultar su resultado.");
        // Una caida del proceso deja EN_CURSO: solo una consulta puede recuperarlo, nunca un POST automatico.
        UUID owner = UUID.randomUUID(); operation.retry(owner); operations.saveAndFlush(operation);
        var document = documents.findById(operation.getDocumentoId()).orElseThrow();
        return new Prepared(saleId,owner,document.getId(),json.read(document.getSnapshotJson(),DataicoSalePayload.class),null);
    }
    private FiscalOperation owned(Prepared prepared) {
        var operation = operations.lockOperation(prepared.saleId()).orElseThrow();
        if (!Objects.equals(operation.getPropietario(),prepared.owner()) || !"EN_CURSO".equals(operation.getEstado()))
            throw new FiscalConflictException("Otro proceso ya resolvio este intento de facturacion.");
        return operation;
    }
    private FiscalDocumentRecord saveRequest(DataicoSalePayload payload) {
        assertUnusedNumber(payload.fullNumber());
        String snapshot = json.write(payload);
        return documents.saveAndFlush(new FiscalDocumentRecord(payload.saleId(),payload.customerId(),"ENVIO","DATAICO",payload.document().ajustes().ambiente(),
                Objects.toString(payload.account(),""),payload.fullNumber(),"DATAICO_DOCUMENT_V1",snapshot,null,json.hash(snapshot),payload.customerName(),payload.total()));
    }
    private void assertUnusedNumber(String number) {
        if (invoices.existsByNumeroFactura(number) || documents.existsByPropositoAndProveedorAndNumero("ENVIO","DATAICO",number))
            throw new FiscalConflictException("El numero " + number + " ya esta registrado o reservado. Revisa la numeracion antes de enviar.");
    }
    private static ResultadoFacturaElectronicaVentaResponse legacyResult(FacturaElectronica invoice) {
        return new ResultadoFacturaElectronicaVentaResponse(invoice.getIdentificadorDataico(),invoice.getNumeroFactura(),invoice.getEstadoDian(),null,null,invoice.getMensajeDian(),invoice.getPdfUrl());
    }
    private static String limit(String value, int size) { return value == null || value.length() <= size ? value : value.substring(0,size); }
    private static String mailStatus(String value, boolean requested) {
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "CORREO_ENVIADO","ENVIADO" -> "ENVIADO"; case "CORREO_FALLO","ERROR" -> "ERROR";
            case "CORREO_NO_ENVIADO","NO_ENVIADO" -> requested ? "NO_ENVIADO" : "NO_SOLICITADO";
            default -> requested ? "PENDIENTE" : "NO_SOLICITADO";
        };
    }
}
