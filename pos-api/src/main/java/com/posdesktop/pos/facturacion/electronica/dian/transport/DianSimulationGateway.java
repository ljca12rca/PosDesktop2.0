package com.posdesktop.pos.facturacion.electronica.dian.transport;

import com.posdesktop.pos.invoicing.infrastructure.signature.DianSoapEnvelope;
import com.posdesktop.pos.invoicing.infrastructure.transport.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

/** Simulacion EN MEMORIA, sin red: prueba el mismo SOAP firmado y el parser de respuestas. */
@Component
public final class DianSimulationGateway implements DianTransportGateway {
    @Override public String mode(){return "SIMULACION";}
    @Override public Response exchange(DianSoapEnvelope.Request request,Context c){
        if(!new DianSoapEnvelope().verify(request.xml(),DianHttpGateway.HABILITATION,Instant.now()))throw new IllegalArgumentException("La solicitud SOAP simulada no tiene firma valida.");
        String result;
        if("SendTestSetAsync".equals(c.method())){
            try{
                var d=DianInvoiceDraftGenerator.parse(request.xml());String content=d.getElementsByTagNameNS(DianSoapResponseParser.WCF,"contentFile").item(0).getTextContent();byte[] zip=Base64.getDecoder().decode(content);
                if(!java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)).equals(c.zipHash()))throw new IllegalArgumentException("El paquete simulado no coincide con su huella.");
            }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Paquete simulado invalido.");}
            result="<SendTestSetAsyncResponse xmlns=\""+DianSoapResponseParser.WCF+"\"><SendTestSetAsyncResult xmlns:u=\""+DianSoapResponseParser.UPLOAD+"\"><u:ErrorMessageList/><u:ZipKey>SIM-"+c.submissionId()+"</u:ZipKey></SendTestSetAsyncResult></SendTestSetAsyncResponse>";
        }else{
            boolean pending=c.queries()<2;String response="<r:IsValid>"+!pending+"</r:IsValid><r:StatusCode>"+(pending?"98":"00")+"</r:StatusCode><r:StatusMessage>SIMULACION LOCAL: "+(pending?"pendiente":"estructura de transporte comprobada; no acredita validacion DIAN")+"</r:StatusMessage><r:XmlDocumentKey>"+c.documentKey()+"</r:XmlDocumentKey><r:XmlFileName>"+c.xmlName()+"</r:XmlFileName>";
            result="<"+c.method()+"Response xmlns=\""+DianSoapResponseParser.WCF+"\"><"+c.method()+"Result xmlns:r=\""+DianSoapResponseParser.RESPONSE+"\">"+("GetStatusZip".equals(c.method())?"<r:DianResponse>"+response+"</r:DianResponse>":response)+"</"+c.method()+"Result></"+c.method()+"Response>";
        }
        return new Response(200,"<s:Envelope xmlns:s=\""+DianSoapResponseParser.SOAP+"\"><s:Body>"+result+"</s:Body></s:Envelope>");
    }
}
