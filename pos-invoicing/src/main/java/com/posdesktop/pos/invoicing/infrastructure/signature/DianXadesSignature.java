package com.posdesktop.pos.invoicing.infrastructure.signature;

import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

/** Firma y verificacion criptografica LOCAL. No acredita confianza ONAC, revocacion ni aceptacion DIAN. */
public final class DianXadesSignature {
    public static final String DS=XMLSignature.XMLNS, XADES="http://uri.etsi.org/01903/v1.3.2#";
    public static final String POLICY="https://facturaelectronica.dian.gov.co/politicadefirma/v2/politicadefirmav2.pdf";
    // SHA-256 de los bytes oficiales descargados el 2026-10-30. No se consulta la red al firmar.
    public static final String POLICY_SHA256="74ca0cbed706e5a233818a34b48b1241e5490439d49df48e7c1a715eb9a8af46";
    private static final String PROPERTIES="http://uri.etsi.org/01903#SignedProperties";
    public record Validation(boolean valid, boolean trustVerified, boolean readyToTransmit, String certificateSha256, List<String> issues) {
        public Validation { issues=List.copyOf(issues); }
    }
    public String sign(String xml, Pkcs12SigningKey material, Instant at) {
        try {
            Document doc=parse(xml);Element root=doc.getDocumentElement();
            requireInvoice(root);
            if(doc.getElementsByTagNameNS(DS,"Signature").getLength()!=0)throw new IllegalArgumentException("El documento ya contiene una firma.");
            Element extensions=direct(root,DianInvoiceDraftGenerator.EXT,"UBLExtensions");
            if(elements(extensions).size()!=1)throw new IllegalArgumentException("El borrador debe contener una sola extension DIAN antes de firmar.");
            Element content=add(add(extensions,DianInvoiceDraftGenerator.EXT,"ext:UBLExtension",null),DianInvoiceDraftGenerator.EXT,"ext:ExtensionContent",null);
            String id="sig-"+UUID.randomUUID(), propsId=id+"-props", keyId=id+"-key", refId=id+"-document";
            var factory=XMLSignatureFactory.getInstance("DOM");
            var sha=factory.newDigestMethod(DigestMethod.SHA256,null);
            var canon=factory.newTransform(CanonicalizationMethod.INCLUSIVE,(javax.xml.crypto.dsig.spec.TransformParameterSpec)null);
            var refs=List.of(factory.newReference("",sha,List.of(factory.newTransform(Transform.ENVELOPED,(javax.xml.crypto.dsig.spec.TransformParameterSpec)null),canon),null,refId),
                    factory.newReference("#"+keyId,sha,List.of(canon),null,null),factory.newReference("#"+propsId,sha,List.of(canon),PROPERTIES,null));
            var info=factory.newSignedInfo(factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE,(javax.xml.crypto.dsig.spec.C14NMethodParameterSpec)null),factory.newSignatureMethod(SignatureMethod.RSA_SHA256,null),refs);
            var kif=factory.getKeyInfoFactory();var keyInfo=kif.newKeyInfo(List.of(kif.newX509Data(material.chain())),keyId);
            Element qualifying=doc.createElementNS(XADES,"xades:QualifyingProperties");
            qualifying.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:xades",XADES);
            qualifying.setAttribute("Target","#"+id);
            Element props=add(qualifying,XADES,"xades:SignedProperties",null);props.setAttribute("Id",propsId);
            Element signed=add(props,XADES,"xades:SignedSignatureProperties",null);
            add(signed,XADES,"xades:SigningTime",at.atOffset(ZoneOffset.ofHours(-5)).toString());
            Element certs=add(signed,XADES,"xades:SigningCertificate",null);
            for(var cert:material.chain()) {
                cert.checkValidity(Date.from(at));
                Element c=add(certs,XADES,"xades:Cert",null);
                digest(add(c,XADES,"xades:CertDigest",null),sha256(cert.getEncoded()));
                Element issuer=add(c,XADES,"xades:IssuerSerial",null);
                add(issuer,DS,"ds:X509IssuerName",cert.getIssuerX500Principal().getName());
                add(issuer,DS,"ds:X509SerialNumber",cert.getSerialNumber().toString());
            }
            Element policy=add(add(signed,XADES,"xades:SignaturePolicyIdentifier",null),XADES,"xades:SignaturePolicyId",null);
            Element policyId=add(policy,XADES,"xades:SigPolicyId",null);
            add(policyId,XADES,"xades:Identifier",POLICY);
            add(policyId,XADES,"xades:Description","Política de firma para facturas electrónicas de la República de Colombia.");
            digest(add(policy,XADES,"xades:SigPolicyHash",null),HexFormat.of().parseHex(POLICY_SHA256));
            add(add(add(signed,XADES,"xades:SignerRole",null),XADES,"xades:ClaimedRoles",null),XADES,"xades:ClaimedRole","supplier");
            Element data=add(props,XADES,"xades:SignedDataObjectProperties",null);
            Element format=add(data,XADES,"xades:DataObjectFormat",null);format.setAttribute("ObjectReference","#"+refId);
            add(format,XADES,"xades:MimeType","text/xml");
            var object=factory.newXMLObject(List.of(new DOMStructure(qualifying)),id+"-object",null,null);
            var context=new DOMSignContext(material.privateKey(),content);context.setDefaultNamespacePrefix("ds");
            context.setIdAttributeNS(props,null,"Id");
            factory.newXMLSignature(info,keyInfo,List.of(object),id,id+"-value").sign(context);
            String result=serialize(doc);
            var checked=verify(result,at);
            if(!checked.valid())throw new IllegalStateException("La firma no supero su verificacion local: "+checked.issues());
            return result;
        } catch(IllegalArgumentException|IllegalStateException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("No se pudo firmar el XML localmente.",e);}
    }
    public Validation verify(String xml, Instant at) {
        try {
            Document doc=parse(xml);Element root=doc.getDocumentElement();requireInvoice(root);
            var signatures=doc.getElementsByTagNameNS(DS,"Signature");check(signatures.getLength()==1,"Debe existir una sola firma.");
            Element signature=(Element)signatures.item(0);
            Element extensions=direct(root,DianInvoiceDraftGenerator.EXT,"UBLExtensions");
            var ext=elements(extensions);check(ext.size()==2 && direct(ext.get(1),DianInvoiceDraftGenerator.EXT,"ExtensionContent")==signature.getParentNode(),"Ubicacion de firma invalida.");
            check(elements((Element)signature.getParentNode()).size()==1,"Extension de firma invalida.");
            Element keyInfo=direct(signature,DS,"KeyInfo");Element x509=direct(keyInfo,DS,"X509Data");
            List<X509Certificate> chain=new ArrayList<>();
            var cf=CertificateFactory.getInstance("X.509");
            for(Element e:elements(x509)) {
                check(DS.equals(e.getNamespaceURI())&&"X509Certificate".equals(e.getLocalName()),"KeyInfo no admitido.");
                chain.add((X509Certificate)cf.generateCertificate(new java.io.ByteArrayInputStream(Base64.getMimeDecoder().decode(e.getTextContent()))));
            }
            check(!chain.isEmpty(),"Falta certificado.");var cert=chain.getFirst();
            check(cert.getPublicKey() instanceof java.security.interfaces.RSAPublicKey rsa && rsa.getModulus().bitLength()>=2048,"Clave RSA insuficiente.");
            Element qualifying=direct(direct(signature,DS,"Object"),XADES,"QualifyingProperties");
            check(("#"+signature.getAttribute("Id")).equals(qualifying.getAttribute("Target")),"Target invalido.");
            Element props=direct(qualifying,XADES,"SignedProperties"),signed=direct(props,XADES,"SignedSignatureProperties");
            Instant signingTime=OffsetDateTime.parse(direct(signed,XADES,"SigningTime").getTextContent()).toInstant();
            check(!signingTime.isAfter(at.plusSeconds(300)),"Hora de firma futura.");
            for(int i=0;i<chain.size();i++) {
                chain.get(i).checkValidity(Date.from(signingTime));chain.get(i).checkValidity(Date.from(at));
                if(i+1<chain.size()) {check(chain.get(i).getIssuerX500Principal().equals(chain.get(i+1).getSubjectX500Principal()),"Cadena desordenada.");chain.get(i).verify(chain.get(i+1).getPublicKey());}
            }
            boolean[] usage=cert.getKeyUsage();check(usage==null || usage[0] || (usage.length>1&&usage[1]),"Certificado sin uso de firma.");
            var signedCerts=elements(direct(signed,XADES,"SigningCertificate"));check(signedCerts.size()==chain.size(),"Cadena no vinculada a las propiedades firmadas.");
            for(int i=0;i<chain.size();i++) {
                Element c=signedCerts.get(i);check(XADES.equals(c.getNamespaceURI())&&"Cert".equals(c.getLocalName()),"Certificado firmado invalido.");
                checkDigest(direct(c,XADES,"CertDigest"),sha256(chain.get(i).getEncoded()));
                Element issuer=direct(c,XADES,"IssuerSerial");
                check(new javax.security.auth.x500.X500Principal(direct(issuer,DS,"X509IssuerName").getTextContent()).equals(chain.get(i).getIssuerX500Principal()),"Emisor del certificado alterado.");
                check(chain.get(i).getSerialNumber().toString().equals(direct(issuer,DS,"X509SerialNumber").getTextContent()),"Serial alterado.");
            }
            Element policy=direct(direct(signed,XADES,"SignaturePolicyIdentifier"),XADES,"SignaturePolicyId");
            check(POLICY.equals(direct(direct(policy,XADES,"SigPolicyId"),XADES,"Identifier").getTextContent()),"Politica de firma diferente.");
            checkDigest(direct(policy,XADES,"SigPolicyHash"),HexFormat.of().parseHex(POLICY_SHA256));
            check("supplier".equals(direct(direct(direct(signed,XADES,"SignerRole"),XADES,"ClaimedRoles"),XADES,"ClaimedRole").getTextContent()),"Rol de firmante invalido.");
            var context=new DOMValidateContext(cert.getPublicKey(),signature);context.setProperty("org.jcp.xml.dsig.secureValidation",Boolean.TRUE);
            Set<String> ids=new HashSet<>();registerIds(root,context,ids);
            var factory=XMLSignatureFactory.getInstance("DOM");
            Set<String> allowed=Set.of("","#"+keyInfo.getAttribute("Id"),"#"+props.getAttribute("Id"));
            var delegate=factory.getURIDereferencer();context.setURIDereferencer((reference,ctx)->{
                if(!allowed.contains(reference.getURI()))throw new javax.xml.crypto.URIReferenceException("Solo referencias internas del perfil.");
                return delegate.dereference(reference,ctx);
            });
            var parsed=factory.unmarshalXMLSignature(context);var info=parsed.getSignedInfo();
            check(SignatureMethod.RSA_SHA256.equals(info.getSignatureMethod().getAlgorithm()) && CanonicalizationMethod.INCLUSIVE.equals(info.getCanonicalizationMethod().getAlgorithm()),"Algoritmos no admitidos.");
            check(info.getReferences().size()==3,"Referencias incompletas.");
            Set<String> seen=new HashSet<>();
            for(Reference ref:info.getReferences()) {
                check(allowed.contains(ref.getURI())&&seen.add(ref.getURI()),"Referencia duplicada o externa.");
                check(DigestMethod.SHA256.equals(ref.getDigestMethod().getAlgorithm()),"Digest no admitido.");
                List<String> transforms=ref.getTransforms().stream().map(Transform::getAlgorithm).toList();
                check(transforms.equals(ref.getURI().isEmpty()?List.of(Transform.ENVELOPED,CanonicalizationMethod.INCLUSIVE):List.of(CanonicalizationMethod.INCLUSIVE)),"Transformaciones diferentes del perfil.");
                check(ref.getURI().equals("#"+props.getAttribute("Id"))?PROPERTIES.equals(ref.getType()):ref.getType()==null,"Tipo de referencia invalido.");
            }
            check(parsed.validate(context),"La firma o los datos firmados fueron alterados.");
            return new Validation(true,false,false,HexFormat.of().formatHex(sha256(cert.getEncoded())),List.of());
        }catch(Exception e){return new Validation(false,false,false,null,List.of("Firma local invalida o XML incompatible con el perfil XAdES implementado."));}
    }
    private static void requireInvoice(Element root){check(Set.of("Invoice","CreditNote","DebitNote").contains(root.getLocalName())&&("urn:oasis:names:specification:ubl:schema:xsd:"+root.getLocalName()+"-2").equals(root.getNamespaceURI()),"Tipo de documento UBL no admitido.");}
    private static Document parse(String xml)throws Exception{if(xml==null||xml.length()>5_000_000)throw new IllegalArgumentException("XML demasiado grande.");return DianInvoiceDraftGenerator.parse(xml);}
    private static void registerIds(Element e,DOMValidateContext ctx,Set<String> ids){
        if(e.hasAttribute("Id")){String id=e.getAttribute("Id");check(!id.isBlank()&&ids.add(id),"Identificador duplicado.");ctx.setIdAttributeNS(e,null,"Id");}
        for(Element child:elements(e))registerIds(child,ctx,ids);
    }
    private static void check(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static byte[] sha256(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    private static void checkDigest(Element e,byte[] expected){check(DigestMethod.SHA256.equals(direct(e,DS,"DigestMethod").getAttribute("Algorithm"))&&MessageDigest.isEqual(expected,Base64.getMimeDecoder().decode(direct(e,DS,"DigestValue").getTextContent())),"Digest de certificado o politica incorrecto.");}
    private static void digest(Element e,byte[] bytes){add(e,DS,"ds:DigestMethod",null).setAttribute("Algorithm",DigestMethod.SHA256);add(e,DS,"ds:DigestValue",Base64.getEncoder().encodeToString(bytes));}
    private static List<Element> elements(Element e){List<Element> result=new ArrayList<>();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element c)result.add(c);return result;}
    private static Element direct(Element e,String ns,String local){var matches=elements(e).stream().filter(c->ns.equals(c.getNamespaceURI())&&local.equals(c.getLocalName())).toList();check(matches.size()==1,"Elemento requerido ausente o duplicado: "+local);return matches.getFirst();}
    private static Element add(Element parent,String ns,String name,String value){Element e=parent.getOwnerDocument().createElementNS(ns,name);if(value!=null)e.setTextContent(value);parent.appendChild(e);return e;}
    private static String serialize(Document doc)throws Exception{var f=TransformerFactory.newInstance();f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");var t=f.newTransformer();t.setOutputProperty(OutputKeys.ENCODING,StandardCharsets.UTF_8.name());t.setOutputProperty(OutputKeys.INDENT,"no");var out=new StringWriter();t.transform(new DOMSource(doc),new StreamResult(out));return out.toString();}
}
