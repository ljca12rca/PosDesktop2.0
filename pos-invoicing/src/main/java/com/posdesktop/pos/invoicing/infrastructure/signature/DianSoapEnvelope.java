package com.posdesktop.pos.invoicing.infrastructure.signature;

import com.posdesktop.pos.invoicing.infrastructure.transport.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.io.StringWriter;
import java.net.URI;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** SOAP 1.2 / WS-Addressing / X509 WS-Security, independiente de la firma XAdES del documento. */
public final class DianSoapEnvelope {
    public static final String WSA="http://www.w3.org/2005/08/addressing";
    public static final String WSU="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    public static final String WSSE="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String X509="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3";
    private static final String THUMB="http://docs.oasis-open.org/wss/oasis-wss-soap-message-security-1.1#ThumbprintSHA1";
    private static final String BASE64="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";
    public record Request(String action,String xml){ }
    public Request send(URI endpoint,DianZipPackage.Archive archive,UUID testSetId,Pkcs12SigningKey key,Instant now){
        Objects.requireNonNull(testSetId);var values=new LinkedHashMap<String,String>();values.put("fileName",archive.zipName());values.put("contentFile",Base64.getEncoder().encodeToString(archive.bytes()));values.put("testSetId",testSetId.toString());return build("SendTestSetAsync",endpoint,values,key,now);
    }
    public Request status(URI endpoint,String trackId,boolean zip,Pkcs12SigningKey key,Instant now){if(trackId==null||trackId.isBlank()||trackId.length()>200)throw new IllegalArgumentException("Falta el identificador de consulta.");return build(zip?"GetStatusZip":"GetStatus",endpoint,Map.of("trackId",trackId),key,now);}
    private Request build(String method,URI endpoint,Map<String,String> values,Pkcs12SigningKey material,Instant now){
        try{
            var doc=DianInvoiceDraftGenerator.parse("<soap:Envelope xmlns:soap=\""+DianSoapResponseParser.SOAP+"\"/>");var root=doc.getDocumentElement();
            for(var ns:Map.of("wsa",WSA,"wcf",DianSoapResponseParser.WCF,"wsse",WSSE,"wsu",WSU,"ds",XMLSignature.XMLNS).entrySet())root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:"+ns.getKey(),ns.getValue());
            var header=add(root,DianSoapResponseParser.SOAP,"soap:Header",null);var body=add(root,DianSoapResponseParser.SOAP,"soap:Body",null);id(body,"body");
            var action=add(header,WSA,"wsa:Action",action(method));id(action,"action");action.setAttributeNS(DianSoapResponseParser.SOAP,"soap:mustUnderstand","1");
            var message=add(header,WSA,"wsa:MessageID","urn:uuid:"+UUID.randomUUID());id(message,"message");
            add(add(header,WSA,"wsa:ReplyTo",null),WSA,"wsa:Address","http://www.w3.org/2005/08/addressing/anonymous");
            var to=add(header,WSA,"wsa:To",endpoint.toString());id(to,"to");to.setAttributeNS(DianSoapResponseParser.SOAP,"soap:mustUnderstand","1");
            var security=add(header,WSSE,"wsse:Security",null);security.setAttributeNS(DianSoapResponseParser.SOAP,"soap:mustUnderstand","1");
            var timestamp=add(security,WSU,"wsu:Timestamp",null);id(timestamp,"timestamp");add(timestamp,WSU,"wsu:Created",now.toString());add(timestamp,WSU,"wsu:Expires",now.plusSeconds(300).toString());
            var cert=material.chain().getFirst();cert.checkValidity(Date.from(now));
            var token=add(security,WSSE,"wsse:BinarySecurityToken",Base64.getEncoder().encodeToString(cert.getEncoded()));id(token,"certificate");token.setAttribute("EncodingType",BASE64);token.setAttribute("ValueType",X509);
            var operation=add(body,DianSoapResponseParser.WCF,"wcf:"+method,null);for(var v:values.entrySet())add(operation,DianSoapResponseParser.WCF,"wcf:"+v.getKey(),v.getValue());
            var factory=XMLSignatureFactory.getInstance("DOM");var sha=factory.newDigestMethod(DigestMethod.SHA256,null);var transform=factory.newTransform(CanonicalizationMethod.EXCLUSIVE,(javax.xml.crypto.dsig.spec.TransformParameterSpec)null);
            var protectedElements=List.of(timestamp,to,body,action,message);List<Reference> references=new ArrayList<>();for(var e:protectedElements)references.add(factory.newReference("#"+e.getAttributeNS(WSU,"Id"),sha,List.of(transform),null,null));
            var info=factory.newSignedInfo(factory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,(javax.xml.crypto.dsig.spec.C14NMethodParameterSpec)null),factory.newSignatureMethod(SignatureMethod.RSA_SHA256,null),references);
            var reference=doc.createElementNS(WSSE,"wsse:SecurityTokenReference");var identifier=add(reference,WSSE,"wsse:KeyIdentifier",Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(cert.getEncoded())));identifier.setAttribute("ValueType",THUMB);identifier.setAttribute("EncodingType",BASE64);
            var keyInfo=factory.getKeyInfoFactory().newKeyInfo(List.of(new DOMStructure(reference)));var context=new DOMSignContext(material.privateKey(),security);context.setDefaultNamespacePrefix("ds");for(var e:protectedElements)context.setIdAttributeNS(e,WSU,"Id");
            factory.newXMLSignature(info,keyInfo).sign(context);String xml=serialize(doc);if(!verify(xml,endpoint,now))throw new IllegalStateException("La firma SOAP no supero la comprobacion local.");return new Request(action(method),xml);
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("No fue posible construir la solicitud SOAP firmada.",e);}
    }
    public boolean verify(String xml,URI endpoint,Instant now){
        try{
            if(xml==null||xml.length()>12_000_000)return false;var doc=DianInvoiceDraftGenerator.parse(xml);var root=doc.getDocumentElement();if(!DianSoapResponseParser.SOAP.equals(root.getNamespaceURI())||!"Envelope".equals(root.getLocalName()))return false;
            var header=child(root,DianSoapResponseParser.SOAP,"Header");var body=child(root,DianSoapResponseParser.SOAP,"Body");var security=child(header,WSSE,"Security");var timestamp=child(security,WSU,"Timestamp");var token=child(security,WSSE,"BinarySecurityToken");var signature=child(security,XMLSignature.XMLNS,"Signature");
            if(doc.getElementsByTagNameNS(XMLSignature.XMLNS,"Signature").getLength()!=1||!endpoint.toString().equals(child(header,WSA,"To").getTextContent())||!X509.equals(token.getAttribute("ValueType"))||!BASE64.equals(token.getAttribute("EncodingType")))return false;
            var created=Instant.parse(child(timestamp,WSU,"Created").getTextContent());var expires=Instant.parse(child(timestamp,WSU,"Expires").getTextContent());if(created.isAfter(now.plusSeconds(60))||!expires.isAfter(now)||!expires.isAfter(created)||expires.isAfter(created.plusSeconds(300)))return false;
            byte[] certBytes=Base64.getMimeDecoder().decode(token.getTextContent());if(certBytes.length>16_384)return false;var cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(certBytes));cert.checkValidity(Date.from(now));
            var keyIdentifier=child(child(child(signature,XMLSignature.XMLNS,"KeyInfo"),WSSE,"SecurityTokenReference"),WSSE,"KeyIdentifier");
            if(!THUMB.equals(keyIdentifier.getAttribute("ValueType"))||!MessageDigest.isEqual(Base64.getMimeDecoder().decode(keyIdentifier.getTextContent()),MessageDigest.getInstance("SHA-1").digest(certBytes)))return false;
            var context=new DOMValidateContext(cert.getPublicKey(),signature);context.setProperty("org.jcp.xml.dsig.secureValidation",true);Set<String> ids=new HashSet<>();register(root,context,ids);
            var expected=new HashSet<String>();for(var e:List.of(timestamp,child(header,WSA,"To"),body,child(header,WSA,"Action"),child(header,WSA,"MessageID")))expected.add("#"+e.getAttributeNS(WSU,"Id"));if(expected.size()!=5)return false;
            var factory=XMLSignatureFactory.getInstance("DOM");var dereferencer=factory.getURIDereferencer();context.setURIDereferencer((ref,ctx)->{if(!expected.contains(ref.getURI()))throw new javax.xml.crypto.URIReferenceException("Referencia externa o no permitida.");return dereferencer.dereference(ref,ctx);});
            var sig=factory.unmarshalXMLSignature(context);if(!SignatureMethod.RSA_SHA256.equals(sig.getSignedInfo().getSignatureMethod().getAlgorithm())||!CanonicalizationMethod.EXCLUSIVE.equals(sig.getSignedInfo().getCanonicalizationMethod().getAlgorithm())||sig.getSignedInfo().getReferences().size()!=5)return false;
            Set<String> seen=new HashSet<>();for(Reference r:sig.getSignedInfo().getReferences())if(!expected.contains(r.getURI())||!seen.add(r.getURI())||!DigestMethod.SHA256.equals(r.getDigestMethod().getAlgorithm())||r.getTransforms().size()!=1||!CanonicalizationMethod.EXCLUSIVE.equals(r.getTransforms().getFirst().getAlgorithm()))return false;
            var operations=new ArrayList<Element>();for(Node n=body.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e)operations.add(e);if(operations.size()!=1||!DianSoapResponseParser.WCF.equals(operations.getFirst().getNamespaceURI())||!Set.of("SendTestSetAsync","GetStatusZip","GetStatus").contains(operations.getFirst().getLocalName())||!action(operations.getFirst().getLocalName()).equals(child(header,WSA,"Action").getTextContent()))return false;
            return sig.validate(context);
        }catch(Exception e){return false;}
    }
    public static String action(String method){if(!Set.of("SendTestSetAsync","GetStatusZip","GetStatus").contains(method))throw new IllegalArgumentException("Accion SOAP invalida.");return "http://wcf.dian.colombia/IWcfDianCustomerServices/"+method;}
    private static void id(Element e,String prefix){e.setAttributeNS(WSU,"wsu:Id",prefix+"-"+UUID.randomUUID());}
    private static Element child(Element e,String ns,String name){return DianDocumentIdentity.child(e,ns,name);}
    private static Element add(Element parent,String ns,String name,String value){var e=parent.getOwnerDocument().createElementNS(ns,name);if(value!=null)e.setTextContent(value);parent.appendChild(e);return e;}
    private static void register(Element e,DOMValidateContext c,Set<String> ids){if(e.hasAttributeNS(WSU,"Id")){String id=e.getAttributeNS(WSU,"Id");if(id.isBlank()||!ids.add(id))throw new IllegalArgumentException("Id duplicado.");c.setIdAttributeNS(e,WSU,"Id");}for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element child)register(child,c,ids);}
    private static String serialize(Document d)throws Exception{var f=TransformerFactory.newInstance();f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");var t=f.newTransformer();t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");var out=new StringWriter();t.transform(new DOMSource(d),new StreamResult(out));return out.toString();}
}
