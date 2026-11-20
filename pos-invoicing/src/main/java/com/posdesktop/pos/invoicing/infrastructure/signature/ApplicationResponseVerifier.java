package com.posdesktop.pos.invoicing.infrastructure.signature;

import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.security.*;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import org.w3c.dom.*;

/** Verificacion sin red. Una firma integra no convierte un certificado embebido en confiable. */
public final class ApplicationResponseVerifier {
    public record Result(boolean signatureValid,boolean correlated,boolean trusted,String certificateSha256,String responseCode,List<String> issues){public Result{issues=List.copyOf(issues);}}
    public Result verify(String xml,String number,String key,Set<String> trustedFingerprints,Instant at){
        try{
            if(xml==null||xml.length()>3_000_000)throw new IllegalArgumentException();
            var d=DianInvoiceDraftGenerator.parse(xml);var root=d.getDocumentElement();
            require("ApplicationResponse".equals(root.getLocalName())&&("urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2").equals(root.getNamespaceURI()));
            var responses=d.getElementsByTagNameNS(DianInvoiceDraftGenerator.CAC,"DocumentResponse");require(responses.getLength()==1);
            var response=(Element)responses.item(0);var references=response.getElementsByTagNameNS(DianInvoiceDraftGenerator.CAC,"DocumentReference");require(references.getLength()==1);
            var reference=(Element)references.item(0);
            boolean correlated=number.equals(one(reference,DianInvoiceDraftGenerator.CBC,"ID").getTextContent())&&key.equals(one(reference,DianInvoiceDraftGenerator.CBC,"UUID").getTextContent());
            String code=one(one(response,DianInvoiceDraftGenerator.CAC,"Response"),DianInvoiceDraftGenerator.CBC,"ResponseCode").getTextContent();
            var signatures=d.getElementsByTagNameNS(XMLSignature.XMLNS,"Signature");require(signatures.getLength()==1);var signature=(Element)signatures.item(0);
            var certificates=one(signature,XMLSignature.XMLNS,"KeyInfo").getElementsByTagNameNS(XMLSignature.XMLNS,"X509Certificate");require(certificates.getLength()>0&&certificates.getLength()<=8);
            byte[] bytes=Base64.getMimeDecoder().decode(certificates.item(0).getTextContent());require(bytes.length<=16_384);
            var cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(bytes));cert.checkValidity(Date.from(at));
            require(cert.getPublicKey() instanceof java.security.interfaces.RSAPublicKey rsa&&rsa.getModulus().bitLength()>=2048);
            boolean[] usage=cert.getKeyUsage();require(usage==null||usage[0]||(usage.length>1&&usage[1]));
            var context=new DOMValidateContext(cert.getPublicKey(),signature);context.setProperty("org.jcp.xml.dsig.secureValidation",true);
            Map<String,Element> ids=new HashMap<>();register(root,context,ids);
            var factory=XMLSignatureFactory.getInstance("DOM");var original=factory.getURIDereferencer();
            context.setURIDereferencer((ref,ctx)->{String uri=ref.getURI();if(!"".equals(uri)&&!(uri!=null&&uri.startsWith("#")&&ids.containsKey(uri.substring(1))&&inside(ids.get(uri.substring(1)),signature)))throw new javax.xml.crypto.URIReferenceException("Referencia no permitida.");return original.dereference(ref,ctx);});
            var parsed=factory.unmarshalXMLSignature(context);var info=parsed.getSignedInfo();require(Set.of(SignatureMethod.RSA_SHA256,SignatureMethod.RSA_SHA384,SignatureMethod.RSA_SHA512).contains(info.getSignatureMethod().getAlgorithm()));require(Set.of(CanonicalizationMethod.EXCLUSIVE,CanonicalizationMethod.INCLUSIVE).contains(info.getCanonicalizationMethod().getAlgorithm()));
            require(!info.getReferences().isEmpty()&&info.getReferences().size()<=3);Set<String> seen=new HashSet<>();boolean coversDocument=false;
            for(Reference r:info.getReferences()){
                require(r.getURI()!=null&&seen.add(r.getURI()));require(Set.of(DigestMethod.SHA256,DigestMethod.SHA384,DigestMethod.SHA512).contains(r.getDigestMethod().getAlgorithm()));
                var transforms=r.getTransforms().stream().map(Transform::getAlgorithm).toList();
                if(r.getURI().isEmpty()){coversDocument=true;require(transforms.equals(List.of(Transform.ENVELOPED,CanonicalizationMethod.INCLUSIVE))||transforms.equals(List.of(Transform.ENVELOPED,CanonicalizationMethod.EXCLUSIVE))||transforms.equals(List.of(Transform.ENVELOPED)));}
                else require(r.getURI().startsWith("#")&&transforms.size()<=1&&transforms.stream().allMatch(t->Set.of(CanonicalizationMethod.EXCLUSIVE,CanonicalizationMethod.INCLUSIVE).contains(t)));
            }
            require(coversDocument&&parsed.validate(context));String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            boolean trusted=trustedFingerprints.contains(hash);List<String> issues=new ArrayList<>();if(!correlated)issues.add("La respuesta corresponde a otro documento.");if(!trusted)issues.add("Certificado sin huella DIAN confiable configurada; no acredita identidad oficial.");
            return new Result(true,correlated,trusted,hash,code,issues);
        }catch(Exception e){return new Result(false,false,false,null,null,List.of("ApplicationResponse ausente, alterado, vencido o incompatible con el perfil de firma admitido."));}
    }
    private static Element one(Element e,String ns,String name){Element found=null;for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element c&&ns.equals(c.getNamespaceURI())&&name.equals(c.getLocalName())){require(found==null);found=c;}require(found!=null);return found;}
    private static void register(Element e,DOMValidateContext context,Map<String,Element> ids){for(String attr:List.of("Id","ID","id"))if(e.hasAttribute(attr)){String id=e.getAttribute(attr);require(!id.isBlank()&&ids.put(id,e)==null);context.setIdAttributeNS(e,null,attr);}for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element c)register(c,context,ids);}
    private static boolean inside(Element e,Element ancestor){for(Node n=e;n!=null;n=n.getParentNode())if(n==ancestor)return true;return false;}
    private static void require(boolean condition){if(!condition)throw new IllegalArgumentException("Firma ApplicationResponse no verificable.");}
}
