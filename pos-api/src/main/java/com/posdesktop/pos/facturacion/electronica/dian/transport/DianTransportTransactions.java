package com.posdesktop.pos.facturacion.electronica.dian.transport;

import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.DianXadesSignature;
import com.posdesktop.pos.invoicing.infrastructure.transport.*;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transacciones cortas: nunca mantiene bloqueos mientras espera al servicio externo. */
@Service
public class DianTransportTransactions {
    private final DianTransportSettingsRepository settings;
    private final DianFileSequenceRepository sequences;
    private final DianSubmissionRepository submissions;
    private final DianExchangeRepository exchanges;
    private final FiscalDocumentRepository documents;
    private final FiscalAdjustmentRepository adjustments;
    private final FiscalEventRepository events;
    private final DianSettingsRepository dian;
    private final FiscalJson json;
    public DianTransportTransactions(DianTransportSettingsRepository settings,DianFileSequenceRepository sequences,DianSubmissionRepository submissions,
            DianExchangeRepository exchanges,FiscalDocumentRepository documents,FiscalAdjustmentRepository adjustments,FiscalEventRepository events,DianSettingsRepository dian,FiscalJson json){
        this.settings=settings;this.sequences=sequences;this.submissions=submissions;this.exchanges=exchanges;this.documents=documents;this.adjustments=adjustments;this.events=events;this.dian=dian;this.json=json;
    }
    public record Claim(DianSubmission submission,UUID owner,String previousState,String previousResponse,boolean query){ }
    @Transactional public DianSubmission prepare(UUID id){
        String mode=settings.lock().orElseThrow().getMode();
        var existing=submissions.findByDocumentIdAndMode(id,mode);if(existing.isPresent()){if("CANCELADO".equals(existing.get().getState()))throw new FiscalConflictException("Este paquete fue descartado sin enviar. Firma una nueva copia para prepararlo con la configuracion corregida.");return existing.get();}
        var d=documents.findById(id).orElseThrow(()->new IllegalArgumentException("No existe la evidencia fiscal."));
        if(!FiscalSignatureService.FORMAT.equals(d.getFormato())||!"DIAN_DIRECT".equals(d.getProveedor())||!"PRUEBAS".equals(d.getAmbiente()))throw new IllegalArgumentException("Selecciona una copia DIAN firmada del ambiente PRUEBAS.");
        if(!json.hash(d.getSnapshotJson()+"\n"+d.getXml()).equals(d.getHuella()))throw new IllegalArgumentException("La evidencia cambio respecto de su huella.");
        var snapshot=json.read(d.getSnapshotJson(),FiscalSignatureService.SignedSnapshot.class);
        var validation=new DianXadesSignature().verify(d.getXml(),Instant.now());
        if(!validation.valid()||!Objects.equals(validation.certificateSha256(),snapshot.certificateSha256()))throw new IllegalArgumentException("La firma del XML no es valida.");
        var source=documents.findById(snapshot.sourceDocumentId()).orElseThrow();
        if(!source.getHuella().equals(snapshot.sourceHash())||!source.getSnapshotJson().equals(snapshot.sourceSnapshot())||!source.getNumero().equals(d.getNumero()))throw new IllegalArgumentException("El origen firmado no coincide con la evidencia guardada.");
        var config=json.read(dian.lockSettings("PRUEBAS").orElseThrow().getDatosJson(),DianConfiguration.class);
        if(config.testSetId()==null)throw new IllegalArgumentException("Configura el TestSetId de PRUEBAS antes de preparar el paquete.");
        checkAdjustment(source.getId());
        var identity=DianDocumentIdentity.read(d.getXml());
        if(!"2".equals(identity.environment())||!config.issuer().identification().equals(identity.issuerNit()))throw new IllegalArgumentException("El XML no corresponde al emisor o ambiente de PRUEBAS configurado.");
        String logical=mode+"|PRUEBAS|"+identity.issuerNit()+"|"+identity.root()+"|"+identity.number();
        if(submissions.findByLogicalKeyOrderByCreatedAtDesc(logical).stream().anyMatch(s->!Set.of("RECHAZADO","CANCELADO").contains(s.getState())))throw new FiscalConflictException("Este numero ya tiene un paquete. Consulta su resultado o descarta el paquete si aun no se envio.");
        int year=Year.now(ZoneId.of("America/Bogota")).getValue();
        String sequenceKey=mode+"|"+identity.issuerNit()+"|"+year;
        var sequence=sequences.findById(sequenceKey).orElseGet(()->new DianFileSequence(sequenceKey));
        var archive=new DianZipPackage().create(d.getXml(),sequence.reserve(),year);sequences.save(sequence);
        var saved=submissions.saveAndFlush(new DianSubmission(id,source.getId(),logical,mode,identity.issuerNit(),identity.number(),identity.root(),identity.key(),config.testSetId(),archive.zipName(),archive.xmlName(),archive.bytes(),hash(archive.bytes()),snapshot.certificateSha256(),snapshot.verificationScope()));
        events.save(new FiscalEvent(id,"DIAN_PAQUETE_PREPARADO",mode+": "+archive.zipName()+". Sin envio."));return saved;
    }
    @Transactional public Claim claim(UUID id,boolean query){
        var found=get(id);checkAdjustment(found.getSourceDocumentId());var s=submissions.lock(id).orElseThrow();
        if("HABILITACION".equals(s.getMode())&&!"ONAC_CHAIN_AND_REVOCATION_VALIDATED".equals(s.getSignatureScope()))throw new IllegalArgumentException("Envio real pendiente: se requiere certificado confiable y verificacion de revocacion. Usa SIMULACION para pruebas locales.");
        if(Set.of("ACEPTADO","RECHAZADO","CANCELADO").contains(s.getState()))throw new FiscalConflictException("El paquete ya esta cerrado. Revisa la evidencia del intento.");
        if("EN_CURSO".equals(s.getState())&&s.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(3)))throw new FiscalConflictException("Hay una solicitud en curso. Espera su resultado.");
        if(query&&"PREPARADO".equals(s.getState()))throw new IllegalArgumentException("Primero envia el paquete preparado.");
        if(!query&&!"PREPARADO".equals(s.getState()))throw new FiscalConflictException("Este paquete ya tuvo un intento de envio. Consulta el estado para evitar duplicados.");
        var claim=new Claim(s,UUID.randomUUID(),s.getState(),s.getResponse(),query);s.claim(claim.owner(),query);submissions.saveAndFlush(s);return claim;
    }
    @Transactional public DianSubmission complete(Claim c,String method,String request,String response,Integer http,DianSoapResponseParser.Reply reply){
        var s=submissions.lock(c.submission().getId()).orElseThrow();
        if(!c.owner().equals(s.getOwner()))throw new FiscalConflictException("Otra consulta tomo el seguimiento. Recarga el estado.");
        String state=switch(reply.state()){case RECEIVED->"RECIBIDO";case PENDING->"PENDIENTE";case ACCEPTED->"ACEPTADO";case REJECTED->"RECHAZADO";default->"INCIERTO";};
        String message=reply.message()==null?"Sin resultado verificable.":reply.message();
        exchanges.save(new DianExchange(s.getId(),method,request,response,http,message));
        s.finish(state,reply.trackId(),message,json.write(reply));
        events.save(new FiscalEvent(s.getDocumentId(),"DIAN_"+state,s.getMode()+": "+message.substring(0,Math.min(450,message.length()))));
        return submissions.saveAndFlush(s);
    }
    @Transactional(readOnly=true) public DianSubmission get(UUID id){return submissions.findById(id).orElseThrow(()->new IllegalArgumentException("No existe el envio DIAN."));}
    @Transactional public DianSubmission cancel(UUID id){var s=submissions.lock(id).orElseThrow();if(!"PREPARADO".equals(s.getState()))throw new FiscalConflictException("Solo puedes descartar un paquete que nunca se ha enviado.");s.finish("CANCELADO",null,"Paquete descartado sin envio. Firma una nueva copia para corregir su configuracion.",null);events.save(new FiscalEvent(s.getDocumentId(),"DIAN_PAQUETE_CANCELADO",s.getMode()+": paquete descartado sin envio; la evidencia y el numero de archivo se conservan."));return submissions.saveAndFlush(s);}
    @Transactional(readOnly=true) public List<DianSubmission> list(UUID document){return submissions.findByDocumentIdOrderByCreatedAtDesc(document);}
    @Transactional(readOnly=true) public List<DianExchange> history(UUID id){get(id);return exchanges.findBySubmissionIdOrderByCreatedAtAsc(id);}
    private void checkAdjustment(UUID source){adjustments.findByDocumentId(source).ifPresent(a->{var locked=adjustments.lock(a.getId()).orElseThrow();if("DESCARTADO".equals(locked.getState()))throw new IllegalArgumentException("Este ajuste fue descartado; no puede enviarse.");});}
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
