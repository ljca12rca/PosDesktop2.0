package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.invoicing.infrastructure.signature.*;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidencia local adicional e inmutable: no reserva numeros, no emite y no llama a proveedores. */
@Service
public class FiscalSignatureService {
    public static final String FORMAT="DIAN_SIGNED_LOCAL_V1";
    private final FiscalDocumentRepository documents;
    private final FiscalEventRepository events;
    private final DianSettingsRepository settings;
    private final FiscalSecretVault vault;
    private final FiscalJson json;
    private final DianXadesSignature signer=new DianXadesSignature();
    public FiscalSignatureService(FiscalDocumentRepository documents,FiscalEventRepository events,DianSettingsRepository settings,FiscalSecretVault vault,FiscalJson json){
        this.documents=documents;this.events=events;this.settings=settings;this.vault=vault;this.json=json;
    }
    public record SignedSnapshot(int schemaVersion,UUID sourceDocumentId,String sourceHash,String sourceSnapshot,
            String certificateSha256,String verificationScope) { }
    public record Result(UUID id,String number,String state,boolean signatureValid,boolean trustVerified,boolean readyToTransmit) { }
    @Transactional
    public Result sign(UUID id) {
        var source=document(id);
        if(!"DIAN_DIRECT".equals(source.getProveedor())||!"BORRADOR".equals(source.getProposito())||!"DIAN_INVOICE_V1".equals(source.getFormato()))
            throw new IllegalArgumentException("Selecciona un borrador DIAN sin firma.");
        if(!"PRUEBAS".equals(source.getAmbiente()))throw new IllegalArgumentException("La firma local de esta etapa solo esta habilitada en PRUEBAS.");
        checkHash(source);
        var row=settings.lockSettings("PRUEBAS").orElseThrow();
        var secrets=vault.decrypt(row.getSecretosCifrados(),"PRUEBAS");
        if(secrets.certificateBase64()==null||secrets.certificatePassword()==null)
            throw new IllegalArgumentException("Carga el certificado .p12 de prueba y su contrasena en Configuracion electronica, ambiente PRUEBAS.");
        byte[] bytes=null;char[] password=secrets.certificatePassword().toCharArray();
        String xml;Instant now=Instant.now();
        try {
            bytes=Base64.getDecoder().decode(secrets.certificateBase64());
            var material=Pkcs12SigningKey.load(bytes,password,now);
            xml=signer.sign(source.getXml(),material,now);
        } finally {if(bytes!=null)Arrays.fill(bytes,(byte)0);Arrays.fill(password,'\0');}
        var validation=signer.verify(xml,now);
        if(!validation.valid())throw new IllegalArgumentException("La firma no supero la validacion local.");
        String snapshot=json.write(new SignedSnapshot(1,source.getId(),source.getHuella(),source.getSnapshotJson(),validation.certificateSha256(),"LOCAL_CRYPTOGRAPHIC_ONLY"));
        var signed=documents.saveAndFlush(new FiscalDocumentRecord(source.getVentaId(),source.getClienteId(),"BORRADOR","DIAN_DIRECT","PRUEBAS",
                source.getCuenta(),source.getNumero(),FORMAT,snapshot,xml,json.hash(snapshot+"\n"+xml),source.getClienteNombre(),source.getTotal()));
        events.save(new FiscalEvent(source.getId(),"COPIA_FIRMADA_LOCAL", "Evidencia firmada: "+signed.getId()+". El borrador original se conserva."));
        events.save(new FiscalEvent(signed.getId(),"FIRMA_LOCAL_VALIDADA","Integridad XAdES verificada. Sin verificacion de confianza ONAC o revocacion; sin envio ni aceptacion DIAN."));
        return new Result(signed.getId(),signed.getNumero(),"FIRMADO_LOCAL",true,false,false);
    }
    @Transactional(readOnly=true)
    public DianXadesSignature.Validation verify(UUID id) {
        var document=document(id);
        if(!FORMAT.equals(document.getFormato()))throw new IllegalArgumentException("Selecciona una evidencia con firma local.");
        checkHash(document);return signer.verify(document.getXml(),Instant.now());
    }
    private FiscalDocumentRecord document(UUID id){return documents.findById(id).orElseThrow(()->new ResourceNotFoundException("No existe el documento fiscal."));}
    private void checkHash(FiscalDocumentRecord doc){if(doc.getXml()==null||!json.hash(doc.getSnapshotJson()+"\n"+doc.getXml()).equals(doc.getHuella()))throw new IllegalArgumentException("La evidencia fiscal no coincide con su huella de integridad.");}
}
