package com.posdesktop.pos.facturacion.electronica.adjustments;

import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.DianAdjustmentGenerator;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Cada mutacion se confirma antes del HTTP. Reserva y snapshot comparten transaccion. */
@Service
public class AdjustmentTransactions {
    private final FiscalAdjustmentRepository adjustments;private final AdjustmentNumberingRepository numbering;
    private final VentaRepositorio sales;private final FiscalDocumentRepository documents;private final FiscalEventRepository events;
    private final DianSettingsRepository dian;private final FiscalSecretVault vault;private final FiscalJson json;
    public AdjustmentTransactions(FiscalAdjustmentRepository adjustments,AdjustmentNumberingRepository numbering,VentaRepositorio sales,
            FiscalDocumentRepository documents,FiscalEventRepository events,DianSettingsRepository dian,FiscalSecretVault vault,FiscalJson json){
        this.adjustments=adjustments;this.numbering=numbering;this.sales=sales;this.documents=documents;this.events=events;this.dian=dian;this.vault=vault;this.json=json;
    }
    public record Request(UUID requestId,String provider,UUID sourceId,String sourceHash,AdjustmentKind kind,String observation,List<AdjustmentCalculator.Selection> lines) { }
    public record Numbering(boolean configured,long version,String creditPrefix,long creditNext,String debitPrefix,long debitNext) { }
    public record Configure(long version,String creditPrefix,long creditNext,String debitPrefix,long debitNext) { }
    public record Quote(String number,AdjustmentCalculator.Plan plan) { }
    public record Claim(UUID id,UUID owner,AdjustmentSnapshot snapshot,String previousState,String previousResponse) { }

    @Transactional(readOnly=true) public Numbering numbering(AdjustmentSource s){return numbering.findById(s.context()).map(this::numbering).orElse(new Numbering(false,-1,"NC",1,"ND",1));}
    private Numbering numbering(AdjustmentNumbering n){return new Numbering(true,n.getVersion(),n.prefix(true),n.next(true),n.prefix(false),n.next(false));}
    @Transactional public Numbering configure(AdjustmentSource source,Configure r){
        if(r.creditPrefix()==null||!r.creditPrefix().matches("[A-Z][A-Z0-9]{0,9}")||r.debitPrefix()==null||!r.debitPrefix().matches("[A-Z][A-Z0-9]{0,9}")||r.creditPrefix().equals(r.debitPrefix())||r.creditNext()<1||r.debitNext()<1)throw new IllegalArgumentException("Usa dos prefijos distintos, de hasta 10 letras/numeros, y consecutivos positivos.");
        var old=numbering.lock(source.context());var row=old.orElseGet(()->new AdjustmentNumbering(source.context()));
        if(old.isPresent()&&r.version()!=row.getVersion()||old.isEmpty()&&r.version()!=-1)throw new FiscalConflictException("La numeracion cambio. Vuelve a cargarla.");
        if(old.isPresent()&&(row.prefix(true).equals(r.creditPrefix())&&r.creditNext()<row.next(true)||row.prefix(false).equals(r.debitPrefix())&&r.debitNext()<row.next(false)))throw new IllegalArgumentException("No retrocedas consecutivos ya reservados. Usa un prefijo nuevo cuando corresponda.");
        row.configure(r.creditPrefix(),r.creditNext(),r.debitPrefix(),r.debitNext());return numbering(numbering.saveAndFlush(row));
    }
    @Transactional(readOnly=true) public List<AdjustmentCalculator.Allocation> reservations(AdjustmentSource source){return active(source).stream().filter(a->!"INCREASE".equals(a.getKind())).flatMap(a->snapshot(a).plan().allocations().stream()).toList();}
    private List<FiscalAdjustmentRecord> active(AdjustmentSource source){return adjustments.findBySourceKeyOrderByCreatedAtAsc(source.key()).stream().filter(a->!"DESCARTADO".equals(a.getState())).toList();}
    @Transactional(readOnly=true) public Quote quote(AdjustmentSource source,Request request){return calculate(source,request);}
    private Quote calculate(AdjustmentSource source,Request r){
        validate(source,r);var active=active(source);
        if(active.stream().anyMatch(a->!a.getSourceHash().equals(source.hash())))throw new FiscalConflictException("La factura original cambio desde un ajuste anterior. Revisa su evidencia antes de continuar.");
        if(active.stream().anyMatch(a->"CANCELLATION".equals(a.getKind())))throw new IllegalArgumentException("Esta factura ya tiene una anulacion guardada. Revisa el historial.");
        var reserved=active.stream().filter(a->!"INCREASE".equals(a.getKind())).flatMap(a->snapshot(a).plan().allocations().stream()).toList();
        var plan=new AdjustmentCalculator().calculate(source.lines(),reserved,!active.isEmpty(),r.kind(),r.lines());
        BigDecimal prior=active.stream().filter(a->!"INCREASE".equals(a.getKind())).map(FiscalAdjustmentRecord::getTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(r.kind().credit()&&prior.add(plan.total()).compareTo(source.total())>0)throw new IllegalArgumentException("Los ajustes superan el total original. Revisa cantidades y redondeos.");
        var n=numbering.findById(source.context()).orElseThrow(()->new IllegalArgumentException("Configura la numeracion de Devolucion y Anulacion antes de guardar."));
        return new Quote(n.prefix(r.kind().credit())+n.next(r.kind().credit()),plan);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public FiscalAdjustmentRecord create(AdjustmentSource source,Request r){
        validate(source,r);var existing=adjustments.findByRequestId(r.requestId());if(existing.isPresent())return replay(existing.get(),r);
        // Mismo orden de bloqueos que los borradores DIAN: configuracion, venta y numeracion.
        DianSettings config="DIAN_DIRECT".equals(source.provider())?dian.lockSettings(source.environment()).orElseThrow():null;
        sales.findForFiscalUpdate(source.saleId()).orElseThrow(()->new ResourceNotFoundException("No existe la venta original."));
        existing=adjustments.findByRequestId(r.requestId());if(existing.isPresent())return replay(existing.get(),r);
        var n=numbering.lock(source.context()).orElseThrow(()->new IllegalArgumentException("Configura la numeracion del modulo."));
        Quote quote=calculate(source,r);boolean credit=r.kind().credit();long sequence=n.next(credit);String prefix=n.prefix(credit);
        if(adjustments.existsBySourceKeyStartingWithAndNumber(source.context()+"|",quote.number()))throw new FiscalConflictException("Este numero ya esta reservado. Corrige la numeracion antes de continuar.");
        OffsetDateTime at=OffsetDateTime.now(ZoneOffset.ofHours(-5));
        Map<String,Object> payload="DATAICO".equals(source.provider())?DataicoAdjustmentGateway.request(source,quote.plan(),prefix,sequence,at,r.observation()):Map.of();
        var snap=new AdjustmentSnapshot(1,json.hash(json.write(r)),source,quote.plan(),prefix,sequence,at,r.observation().trim(),payload);
        String xml=config==null?null:generateXml(snap,config);
        String serialized=json.write(snap),hash=json.hash(serialized+"\n"+Objects.toString(xml,""));
        String format=config==null?"DATAICO_ADJUSTMENT_V1":credit?"DIAN_CREDIT_NOTE_V1":"DIAN_DEBIT_NOTE_V1";
        var doc=documents.saveAndFlush(new FiscalDocumentRecord(source.saleId(),source.customerId(),"BORRADOR",source.provider(),source.environment(),source.account(),snap.number(),format,serialized,xml,hash,source.customer(),quote.plan().total()));
        var record=adjustments.saveAndFlush(new FiscalAdjustmentRecord(r.requestId(),source.saleId(),doc.getId(),source.key(),source.hash(),source.number(),source.provider(),source.environment(),r.kind().name(),snap.number(),quote.plan().total(),serialized));
        n.reserve(credit);numbering.save(n);events.save(new FiscalEvent(doc.getId(),"AJUSTE_PREPARADO",r.kind().label()+" sobre "+source.number()+". Sin envio ni movimiento automatico de caja."));return record;
    }
    private FiscalAdjustmentRecord replay(FiscalAdjustmentRecord a,Request request){if(!snapshot(a).requestHash().equals(json.hash(json.write(request))))throw new FiscalConflictException("La solicitud ya existe con otros datos. Vuelve a cargar el ajuste.");return a;}
    private String generateXml(AdjustmentSnapshot s,DianSettings settings){
        if(!"PRUEBAS".equals(s.source().environment()))throw new IllegalArgumentException("DIAN directa esta disponible como borrador de PRUEBAS.");
        var saved=json.read(settings.getDatosJson(),DianConfiguration.class);var original=s.source().dian();
        if(!saved.softwareId().equals(original.profile().softwareId())||!saved.issuer().identification().equals(original.invoice().issuer().identification()))throw new IllegalArgumentException("El software o emisor DIAN cambio. Restaura la configuracion de la factura original.");
        var old=original.invoice();var lines=s.plan().allocations().stream().map(AdjustmentCalculator.Allocation::line).toList();var date=s.issuedAt().toLocalDate();
        var note=new FiscalInvoice(old.sourceSaleId(),s.number(),s.issuedAt(),old.currency(),old.issuer(),old.customer(),new FiscalInvoice.Numbering(s.prefix(),"1",date,date,1,Long.MAX_VALUE),new FiscalInvoice.Payment(old.payment().meansCode(),date,old.number()),lines,s.plan().total());
        var stored=vault.decrypt(settings.getSecretosCifrados(),s.source().environment());if(stored.softwarePin()==null)throw new IllegalArgumentException("Configura el PIN de software DIAN.");
        char[] pin=stored.softwarePin().toCharArray();
        try(var secrets=new DianSecrets("NOT-USED-IN-NOTES".toCharArray(),pin)){
            return new DianAdjustmentGenerator().generate(note,original.profile(),secrets,s.plan().kind(),new DianAdjustmentGenerator.Reference(old.number(),s.source().cufe(),s.source().date()),s.observation()).xml();
        }finally{Arrays.fill(pin,'\0');}
    }
    private void validate(AdjustmentSource s,Request r){
        if(r.requestId()==null||r.kind()==null||r.lines()==null||r.lines().size()>500||r.observation()==null||r.observation().isBlank()||r.observation().length()>500)throw new IllegalArgumentException("Completa la operacion, su motivo (hasta 500 caracteres) y los articulos.");
        if(!s.provider().equals(r.provider())||!s.id().equals(r.sourceId())||!s.hash().equals(r.sourceHash()))throw new FiscalConflictException("La factura cambio. Buscala nuevamente antes de revisar el ajuste.");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public Claim claim(UUID id,boolean consult){
        var a=lock(id);if(!"DATAICO".equals(a.getProvider()))throw new IllegalArgumentException("El transporte DIAN directo sigue pendiente. Puedes guardar y firmar el XML local.");
        if(consult){if(!Set.of("INCIERTO","EN_CURSO","RECIBIDO").contains(a.getState()))throw new IllegalArgumentException("Este ajuste no tiene un envio para consultar.");if("EN_CURSO".equals(a.getState())&&a.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(3)))throw new FiscalConflictException("El envio sigue en curso. Espera antes de consultar.");}
        else if(!Set.of("BORRADOR","ERROR_SEGURO").contains(a.getState()))throw new FiscalConflictException("No se permite otro envio. Consulta el intento existente.");
        UUID owner=UUID.randomUUID();String previousState=a.getState(),previousResponse=a.getResponse();a.transition("EN_CURSO",owner,a.getResponse());adjustments.saveAndFlush(a);events.save(new FiscalEvent(a.getDocumentId(),consult?"AJUSTE_CONSULTA":"AJUSTE_ENVIO","Intento sobre el snapshot y numero previamente guardados."));return new Claim(a.getId(),owner,snapshot(a),previousState,previousResponse);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public FiscalAdjustmentRecord complete(Claim claim,AdjustmentGateway.Result result){var a=owned(claim);if(result==null||result.id()==null||result.id().isBlank()||!a.getNumber().equals(result.number()))throw new FiscalConflictException("El proveedor no confirmo la identidad y numero del ajuste.");a.transition("RECIBIDO",null,json.write(result));events.save(new FiscalEvent(a.getDocumentId(),"AJUSTE_RECIBIDO","Dataico confirmo el registro. Consulta su estado DIAN en la evidencia."));return adjustments.saveAndFlush(a);}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public FiscalAdjustmentRecord failure(Claim claim,boolean certain,String message){var a=owned(claim);
        if("RECIBIDO".equals(claim.previousState()))a.transition("RECIBIDO",null,claim.previousResponse());else a.transition(certain?"ERROR_SEGURO":"INCIERTO",null,json.write(new AdjustmentGateway.Result(null,a.getNumber(),null,null,null,message)));
        events.save(new FiscalEvent(a.getDocumentId(),"AJUSTE_SIN_CONFIRMAR",certain?"Solicitud rechazada sin confirmacion de registro.":"Consulta o envio sin confirmar. No repetir un envio registrado."));return adjustments.saveAndFlush(a);}
    @Transactional public FiscalAdjustmentRecord discard(UUID id){var found=adjustments.findById(id).orElseThrow();sales.findForFiscalUpdate(found.getSaleId()).orElseThrow();var a=lock(id);if(!Set.of("BORRADOR","ERROR_SEGURO").contains(a.getState()))throw new IllegalArgumentException("Solo puedes descartar un borrador o rechazo seguro sin envio confirmado.");a.transition("DESCARTADO",null,a.getResponse());events.save(new FiscalEvent(a.getDocumentId(),"AJUSTE_DESCARTADO","Cantidades liberadas. El numero y la evidencia se conservan."));return adjustments.saveAndFlush(a);}
    @Transactional(readOnly=true) public List<FiscalAdjustmentRecord> history(){return adjustments.findTop100ByOrderByCreatedAtDesc();}
    @Transactional(readOnly=true) public FiscalAdjustmentRecord get(UUID id){return adjustments.findById(id).orElseThrow(()->new ResourceNotFoundException("No existe el ajuste."));}
    private FiscalAdjustmentRecord owned(Claim claim){var a=lock(claim.id());if(!"EN_CURSO".equals(a.getState())||!Objects.equals(a.getOwner(),claim.owner()))throw new FiscalConflictException("El intento pertenece a otra operacion. Consulta el historial.");return a;}
    private FiscalAdjustmentRecord lock(UUID id){return adjustments.lock(id).orElseThrow(()->new ResourceNotFoundException("No existe el ajuste."));}
    public AdjustmentSnapshot snapshot(FiscalAdjustmentRecord a){return json.read(a.getSnapshot(),AdjustmentSnapshot.class);}
}
