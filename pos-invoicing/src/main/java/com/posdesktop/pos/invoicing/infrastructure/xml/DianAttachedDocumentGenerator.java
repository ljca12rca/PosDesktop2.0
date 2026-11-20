package com.posdesktop.pos.invoicing.infrastructure.xml;

import static com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.ApplicationResponseVerifier;
import com.posdesktop.pos.invoicing.infrastructure.transport.DianDocumentIdentity;
import java.io.StringWriter;
import java.time.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Contenedor local para completar la entrega en pruebas. No inventa una respuesta DIAN. */
public final class DianAttachedDocumentGenerator {
 public String generate(String sourceXml,String responseXml,UUID id,Instant at){try{
  var identity=DianDocumentIdentity.read(sourceXml);if(!"2".equals(identity.environment()))throw new IllegalArgumentException("Contenedor local disponible solo en PRUEBAS.");
  var verification=new ApplicationResponseVerifier().verify(responseXml,identity.number(),identity.key(),Set.of(),at);
  if(!verification.signatureValid()||!verification.correlated()||!"02".equals(verification.responseCode()))throw new IllegalArgumentException("Se requiere ApplicationResponse positivo, integro y correlacionado.");
  var original=parse(sourceXml).getDocumentElement();var response=parse(responseXml).getDocumentElement();
  var d=parse("<AttachedDocument xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:AttachedDocument-2\" xmlns:cbc=\""+CBC+"\" xmlns:cac=\""+CAC+"\" xmlns:ext=\""+EXT+"\"/>");var root=d.getDocumentElement();add(root,EXT,"ext:UBLExtensions",null);
  add(root,CBC,"cbc:UBLVersionID","UBL 2.1");add(root,CBC,"cbc:CustomizationID","Documentos adjuntos");add(root,CBC,"cbc:ProfileID","Factura Electrónica de Venta");add(root,CBC,"cbc:ProfileExecutionID","2");add(root,CBC,"cbc:ID",id.toString());
  var now=at.atOffset(ZoneOffset.ofHours(-5));LocalDate responseDate=LocalDate.parse(child(response,CBC,"IssueDate").getTextContent());if(now.toLocalDate().isBefore(identity.date())||now.toLocalDate().isBefore(responseDate))throw new IllegalArgumentException("La fecha del contenedor no puede preceder a sus documentos.");
  add(root,CBC,"cbc:IssueDate",now.toLocalDate().toString());add(root,CBC,"cbc:IssueTime",now.toOffsetTime().toString());add(root,CBC,"cbc:Note","PRUEBAS - SIN VALIDEZ FISCAL. No acredita confianza del certificado DIAN ni expedicion.");add(root,CBC,"cbc:DocumentType","Contenedor de Factura Electrónica");add(root,CBC,"cbc:ParentDocumentID",identity.number());
  party(root,"SenderParty",child(child(original,CAC,"AccountingSupplierParty"),CAC,"Party"));party(root,"ReceiverParty",child(child(original,CAC,"AccountingCustomerParty"),CAC,"Party"));attachment(root,sourceXml);
  var line=add(root,CAC,"cac:ParentDocumentLineReference",null);add(line,CBC,"cbc:LineID","1");var reference=add(line,CAC,"cac:DocumentReference",null);add(reference,CBC,"cbc:ID",identity.number());var uuid=add(reference,CBC,"cbc:UUID",identity.key());uuid.setAttribute("schemeName","Invoice".equals(identity.root())?"CUFE-SHA384":"CUDE-SHA384");add(reference,CBC,"cbc:IssueDate",responseDate.toString());add(reference,CBC,"cbc:DocumentType","ApplicationResponse");attachment(reference,responseXml);
  var result=add(reference,CAC,"cac:ResultOfVerification",null);add(result,CBC,"cbc:ValidatorID","Unidad Especial Dirección de Impuestos y Aduanas Nacionales");add(result,CBC,"cbc:ValidationResultCode",verification.responseCode());add(result,CBC,"cbc:ValidationDate",responseDate.toString());add(result,CBC,"cbc:ValidationTime",child(response,CBC,"IssueTime").getTextContent());
  var factory=TransformerFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");var writer=new StringWriter();factory.newTransformer().transform(new DOMSource(d),new StreamResult(writer));return writer.toString();
 }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("No fue posible construir el contenedor de prueba.",e);}}
 private static void party(Element root,String role,Element source){var target=add(root,CAC,"cac:"+role,null);target.appendChild(root.getOwnerDocument().importNode(child(source,CAC,"PartyTaxScheme"),true));}
 private static void attachment(Element parent,String xml){var reference=add(add(parent,CAC,"cac:Attachment",null),CAC,"cac:ExternalReference",null);add(reference,CBC,"cbc:MimeCode","text/xml");add(reference,CBC,"cbc:EncodingCode","UTF-8");add(reference,CBC,"cbc:Description",xml);}
 private static Element child(Element parent,String ns,String local){return DianDocumentIdentity.child(parent,ns,local);}
 private static Element add(Element parent,String ns,String name,String value){var e=parent.getOwnerDocument().createElementNS(ns,name);if(value!=null)e.setTextContent(value);parent.appendChild(e);return e;}
}
