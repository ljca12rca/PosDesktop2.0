package com.posdesktop.pos.facturacion.electronica.dian.transport;

import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalSecretVault;
import com.posdesktop.pos.invoicing.infrastructure.signature.*;
import com.posdesktop.pos.invoicing.infrastructure.transport.*;
import com.posdesktop.pos.modelo.relacional.DianSubmission;
import com.posdesktop.pos.repositorio.relacional.DianSettingsRepository;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;

/** HTTP fuera de la transaccion; ante un fallo conserva el intento y permite consultar, nunca repite POST. */
@Service
public class DianTransportService {
    private static final Logger log=LoggerFactory.getLogger(DianTransportService.class);
    private final DianTransportTransactions transactions;private final DianTransportGateways gateways;private final DianSettingsRepository settings;private final FiscalSecretVault vault;
    private final com.posdesktop.pos.facturacion.electronica.delivery.FiscalDeliverySettingsService delivery;
    public DianTransportService(DianTransportTransactions transactions,DianTransportGateways gateways,DianSettingsRepository settings,FiscalSecretVault vault,com.posdesktop.pos.facturacion.electronica.delivery.FiscalDeliverySettingsService delivery){this.transactions=transactions;this.gateways=gateways;this.settings=settings;this.vault=vault;this.delivery=delivery;}
    public DianSubmission execute(UUID id,boolean query){
        // Fallos locales de credenciales se detectan antes de registrar un intento externo.
        var s=transactions.get(id);var material=key(s);var c=transactions.claim(id,query);s=c.submission();
        String method=query?(s.getTrackId()==null?"GetStatus":"GetStatusZip"):"SendTestSetAsync";
        String request="";DianTransportGateway.Response response=null;DianSoapResponseParser.Reply reply;
        try{
            var soap=new DianSoapEnvelope();
            var r=query?soap.status(DianHttpGateway.HABILITATION,s.getTrackId()==null?s.getDocumentKey():s.getTrackId(),s.getTrackId()!=null,material,Instant.now())
                    :soap.send(DianHttpGateway.HABILITATION,new DianZipPackage.Archive(s.getZipName(),s.getXmlName(),s.getZipBytes()),s.getTestSetId(),material,Instant.now());
            request=r.xml();log.info("DIAN transporte {}: envio={}, modo={}, archivo={}",method,s.getId(),s.getMode(),s.getZipName());
            response=gateways.get(s.getMode()).exchange(r,new DianTransportGateway.Context(s.getId(),method,s.getZipName(),s.getXmlName(),s.getDocumentKey(),s.getZipHash(),s.getQueries()));
            var parser=new DianSoapResponseParser();reply=query?parser.status(response.body(),method,s.getDocumentKey(),s.getXmlName()):parser.upload(response.body());
            if(response.httpStatus()<200||response.httpStatus()>=300)reply=new DianSoapResponseParser.Reply(DianSoapResponseParser.State.UNKNOWN,null,null,"Error HTTP "+response.httpStatus()+". Consulta este intento antes de reenviar.",reply.errors(),null);
            if(query&&"HABILITACION".equals(s.getMode())&&reply.state()==DianSoapResponseParser.State.ACCEPTED){var verification=new ApplicationResponseVerifier().verify(reply.applicationResponse(),s.getNumber(),s.getDocumentKey(),delivery.get().configuration().responseCertificateFingerprints(),Instant.now());if(!verification.signatureValid()||!verification.correlated()||!verification.trusted()||!"02".equals(verification.responseCode()))reply=new DianSoapResponseParser.Reply(DianSoapResponseParser.State.UNKNOWN,null,reply.code(),"Respuesta positiva sin firma DIAN confiable y correlacionada. Revisa la evidencia antes de acreditar aceptacion.",verification.issues(),reply.applicationResponse());}
        }catch(Exception e){log.warn("Fallo tecnico DIAN: envio={}, modo={}, operacion={}",s.getId(),s.getMode(),method);reply=new DianSoapResponseParser.Reply(DianSoapResponseParser.State.UNKNOWN,null,null,"No se pudo confirmar el resultado. Consulta el estado del intento existente.",List.of(),null);}
        return transactions.complete(c,method,request,response==null?null:response.body(),response==null?null:response.httpStatus(),reply);
    }
    private Pkcs12SigningKey key(DianSubmission s){
        var secrets=vault.decrypt(settings.findById("PRUEBAS").orElseThrow().getSecretosCifrados(),"PRUEBAS");
        if(secrets.certificateBase64()==null||secrets.certificatePassword()==null)throw new IllegalArgumentException("Carga el certificado de PRUEBAS para firmar el transporte SOAP.");
        byte[] bytes=Base64.getDecoder().decode(secrets.certificateBase64());char[] password=secrets.certificatePassword().toCharArray();
        try{var key=Pkcs12SigningKey.load(bytes,password,Instant.now());String hash=DianTransportTransactions.hash(key.chain().getFirst().getEncoded());if(!hash.equals(s.getCertificateHash()))throw new IllegalArgumentException("El certificado cambio desde la firma del documento. Restaura el certificado original para consultar este paquete.");return key;}
        catch(java.security.cert.CertificateEncodingException e){throw new IllegalArgumentException("Certificado invalido.");}
        finally{Arrays.fill(bytes,(byte)0);Arrays.fill(password,'\0');}
    }
}
