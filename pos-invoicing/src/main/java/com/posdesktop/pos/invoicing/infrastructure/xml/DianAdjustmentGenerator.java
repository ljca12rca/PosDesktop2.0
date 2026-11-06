package com.posdesktop.pos.invoicing.infrastructure.xml;

import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import java.io.StringWriter;
import java.time.LocalDate;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Construye notas referenciadas, reutilizando calculos, partes y tributos del perfil nacional. */
public final class DianAdjustmentGenerator {
    private static final String CBC=DianInvoiceDraftGenerator.CBC,CAC=DianInvoiceDraftGenerator.CAC,STS=DianInvoiceDraftGenerator.STS;
    public record Reference(String number,String cufe,LocalDate date) {
        public Reference {if(number==null||number.isBlank()||cufe==null||!cufe.matches("[a-fA-F0-9]{96}")||date==null)throw new IllegalArgumentException("La factura requiere numero, CUFE y fecha originales.");}
    }
    public record Draft(String xml,String cude,boolean readyToTransmit) { }
    public Draft generate(FiscalInvoice amounts,DianProfile profile,DianSecrets secrets,AdjustmentKind kind,Reference reference,String observation) {
        if(amounts.issuedAt().toLocalDate().isBefore(reference.date()))throw new IllegalArgumentException("El ajuste no puede ser anterior a la factura.");
        if(!amounts.withholdings().isEmpty())throw new IllegalArgumentException("Las retenciones de notas requieren un perfil especifico.");
        try {
            var original=new DianInvoiceDraftGenerator().generate(amounts,profile,secrets);
            var doc=DianInvoiceDraftGenerator.parse(original.xml());
            String name=kind.root(),ns="urn:oasis:names:specification:ubl:schema:xsd:"+name+"-2";
            Element root=(Element)doc.renameNode(doc.getDocumentElement(),ns,name);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns",ns);
            text(root,CBC,"CustomizationID",kind.credit()?"20":"30");
            text(root,CBC,"ProfileID",kind.credit()?"DIAN 2.1: Nota Crédito de Factura Electrónica de Venta":"DIAN 2.1: Nota Débito de Factura Electrónica de Venta");
            Element type=child(root,CBC,"InvoiceTypeCode");
            if(kind.credit()){doc.renameNode(type,CBC,"cbc:CreditNoteTypeCode");type.setTextContent("91");}else root.removeChild(type);
            text(root,CBC,"Note",observation);
            var control=doc.getElementsByTagNameNS(STS,"InvoiceControl");if(control.getLength()>0)control.item(0).getParentNode().removeChild(control.item(0));
            String cude=DianIdentifiers.cude(amounts,new InvoiceCalculator().calculate(amounts),profile,secrets);
            Element uuid=child(root,CBC,"UUID");uuid.setTextContent(cude);uuid.setAttribute("schemeName","CUDE-SHA384");
            doc.getElementsByTagNameNS(STS,"QRCode").item(0).setTextContent(profile.environment().qr(cude));
            Element supplier=child(root,CAC,"AccountingSupplierParty");
            Element discrepancy=doc.createElementNS(CAC,"cac:DiscrepancyResponse");root.insertBefore(discrepancy,supplier);
            add(discrepancy,CBC,"cbc:ReferenceID",reference.number());add(discrepancy,CBC,"cbc:ResponseCode",kind.reason());add(discrepancy,CBC,"cbc:Description",observation);
            Element billing=doc.createElementNS(CAC,"cac:BillingReference");root.insertBefore(billing,supplier);
            Element invoice=add(billing,CAC,"cac:InvoiceDocumentReference",null);add(invoice,CBC,"cbc:ID",reference.number());
            Element sourceUuid=add(invoice,CBC,"cbc:UUID",reference.cufe());sourceUuid.setAttribute("schemeName","CUFE-SHA384");
            add(invoice,CBC,"cbc:IssueDate",reference.date().toString());
            var nodes=doc.getElementsByTagNameNS(CAC,"InvoiceLine");var lines=new java.util.ArrayList<Element>();
            for(int i=0;i<nodes.getLength();i++)lines.add((Element)nodes.item(i));
            for(Element line:lines){
                doc.renameNode(child(line,CBC,"InvoicedQuantity"),CBC,kind.credit()?"cbc:CreditedQuantity":"cbc:DebitedQuantity");
                var children=new java.util.ArrayList<Element>();for(Node n=line.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e)children.add(e);
                Node before=children.stream().filter(e->CAC.equals(e.getNamespaceURI())&&"AllowanceCharge".equals(e.getLocalName())).findFirst().orElse(child(line,CAC,"Item"));
                for(Element e:children){if(CAC.equals(e.getNamespaceURI())&&"TaxTotal".equals(e.getLocalName()))line.insertBefore(e,before);if(CBC.equals(e.getNamespaceURI())&&"Note".equals(e.getLocalName()))line.removeChild(e);}
                doc.renameNode(line,CAC,kind.credit()?"cac:CreditNoteLine":"cac:DebitNoteLine");
            }
            if(!kind.credit())doc.renameNode(child(root,CAC,"LegalMonetaryTotal"),CAC,"cac:RequestedMonetaryTotal");
            var f=TransformerFactory.newInstance();f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            var t=f.newTransformer();t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");var out=new StringWriter();t.transform(new DOMSource(doc),new StreamResult(out));
            String xml=out.toString();var validation=OfflineUblValidator.dian19Note(kind.credit()).validate(xml);
            if(!validation.structurallyValid())throw new IllegalArgumentException("Estructura de ajuste invalida: "+validation.issues());
            return new Draft(xml,cude,false);
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("No se pudo generar el XML del ajuste.",e);}
    }
    private static void text(Element root,String ns,String name,String value){child(root,ns,name).setTextContent(value);}
    private static Element child(Element root,String ns,String name){for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&ns.equals(e.getNamespaceURI())&&name.equals(e.getLocalName()))return e;throw new IllegalArgumentException("Elemento requerido: "+name);}
    private static Element add(Element parent,String ns,String name,String value){Element e=parent.getOwnerDocument().createElementNS(ns,name);if(value!=null)e.setTextContent(value);parent.appendChild(e);return e;}
}
