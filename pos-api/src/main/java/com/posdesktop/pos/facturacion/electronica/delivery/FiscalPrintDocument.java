package com.posdesktop.pos.facturacion.electronica.delivery;

import static com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator.*;
import com.posdesktop.pos.invoicing.infrastructure.transport.DianDocumentIdentity;
import java.util.*;
import org.w3c.dom.*;

/** Representacion leida del XML guardado; no consulta precios ni clientes vivos. */
public record FiscalPrintDocument(String kind,String number,String key,String issued,String issuer,String issuerId,String issuerAddress,
        String customer,String customerId,String customerAddress,String currency,String resolution,String reference,String qr,List<Line> lines,List<String> totals,List<String> metadata){
    public record Line(String description,String quantity,String unit,String unitPrice,String net,String taxes){}
    public FiscalPrintDocument{lines=List.copyOf(lines);totals=List.copyOf(totals);metadata=List.copyOf(metadata);}
    public static FiscalPrintDocument read(String xml){try{
        var identity=DianDocumentIdentity.read(xml);Element root=parse(xml).getDocumentElement();
        var supplier=child(child(root,CAC,"AccountingSupplierParty"),CAC,"Party");var customer=child(child(root,CAC,"AccountingCustomerParty"),CAC,"Party");
        String lineName=switch(identity.root()){case "Invoice"->"InvoiceLine";case "CreditNote"->"CreditNoteLine";default->"DebitNoteLine";};
        List<Line> lines=new ArrayList<>();for(var line:children(root,CAC,lineName)){
            String quantityName=switch(identity.root()){case "Invoice"->"InvoicedQuantity";case "CreditNote"->"CreditedQuantity";default->"DebitedQuantity";};
            var quantity=child(line,CBC,quantityName);List<String> taxes=new ArrayList<>();for(var tax:children(line,CAC,"TaxTotal"))for(var subtotal:children(tax,CAC,"TaxSubtotal")){var category=child(subtotal,CAC,"TaxCategory");taxes.add(value(child(category,CAC,"TaxScheme"),CBC,"Name")+" "+value(category,CBC,"Percent")+"%: "+value(subtotal,CBC,"TaxAmount"));}
            String description=value(child(line,CAC,"Item"),CBC,"Description");
            for(var adjustment:children(line,CAC,"AllowanceCharge"))description+=" · "+("true".equals(value(adjustment,CBC,"ChargeIndicator"))?"Cargo ":"Descuento ")+value(adjustment,CBC,"Amount");
            lines.add(new Line(description,quantity.getTextContent(),quantity.getAttribute("unitCode"),value(child(line,CAC,"Price"),CBC,"PriceAmount"),value(line,CBC,"LineExtensionAmount"),taxes.isEmpty()?"Tratamiento sin tributos":String.join("; ",taxes)));
        }
        List<String> totals=new ArrayList<>();var monetary=child(root,CAC,"DebitNote".equals(identity.root())?"RequestedMonetaryTotal":"LegalMonetaryTotal");
        totals.add("Subtotal: "+value(monetary,CBC,"LineExtensionAmount"));
        for(var tax:children(root,CAC,"TaxTotal"))for(var subtotal:children(tax,CAC,"TaxSubtotal"))totals.add(value(child(child(subtotal,CAC,"TaxCategory"),CAC,"TaxScheme"),CBC,"Name")+": "+value(subtotal,CBC,"TaxAmount"));
        for(var adjustment:children(root,CAC,"AllowanceCharge"))totals.add(("true".equals(value(adjustment,CBC,"ChargeIndicator"))?"Cargo: ":"Descuento: ")+value(adjustment,CBC,"Amount"));
        for(var tax:children(root,CAC,"WithholdingTaxTotal"))totals.add("Retencion informativa: "+value(tax,CBC,"TaxAmount"));
        String rounding=value(monetary,CBC,"PayableRoundingAmount");if(!rounding.isBlank())totals.add("Redondeo: "+rounding);
        totals.add("TOTAL "+value(root,CBC,"DocumentCurrencyCode")+": "+value(monetary,CBC,"PayableAmount"));
        String reference="";var billing=children(root,CAC,"BillingReference");if(!billing.isEmpty())reference=value(child(billing.getFirst(),CAC,"InvoiceDocumentReference"),CBC,"ID");
        var resolutions=root.getElementsByTagNameNS(STS,"InvoiceAuthorization");String resolution=resolutions.getLength()==1?resolutions.item(0).getTextContent():"";
        String qr=("2".equals(identity.environment())?"https://catalogo-vpfe-hab.dian.gov.co":"https://catalogo-vpfe.dian.gov.co")+"/document/searchqr?documentkey="+identity.key();
        List<String> metadata=new ArrayList<>();metadata.add("Responsabilidades emisor: "+value(child(supplier,CAC,"PartyTaxScheme"),CBC,"TaxLevelCode"));metadata.add("Regimen tributario emisor: "+value(child(child(supplier,CAC,"PartyTaxScheme"),CAC,"TaxScheme"),CBC,"Name"));metadata.add("Responsabilidades cliente: "+value(child(customer,CAC,"PartyTaxScheme"),CBC,"TaxLevelCode"));
        for(var payment:children(root,CAC,"PaymentMeans")){metadata.add("Forma de pago: "+("1".equals(value(payment,CBC,"ID"))?"Contado":"Credito"));metadata.add("Medio de pago (DIAN): "+value(payment,CBC,"PaymentMeansCode"));metadata.add("Vencimiento: "+value(payment,CBC,"PaymentDueDate"));}
        var controls=root.getElementsByTagNameNS(STS,"InvoiceControl");if(controls.getLength()==1){var control=(Element)controls.item(0);var period=child(control,STS,"AuthorizationPeriod");var authorized=child(control,STS,"AuthorizedInvoices");metadata.add("Autorizacion: "+value(period,CBC,"StartDate")+" a "+value(period,CBC,"EndDate"));metadata.add("Prefijo: "+value(authorized,STS,"Prefix")+" | Rango: "+value(authorized,STS,"From")+" a "+value(authorized,STS,"To"));}
        return new FiscalPrintDocument(identity.root(),identity.number(),identity.key(),value(root,CBC,"IssueDate")+" "+value(root,CBC,"IssueTime"),name(supplier),identification(supplier),address(supplier),name(customer),identification(customer),address(customer),value(root,CBC,"DocumentCurrencyCode"),resolution,reference,qr,lines,totals,metadata);
    }catch(Exception e){throw new IllegalArgumentException("No fue posible leer la representacion del XML fiscal.",e);}}
    private static String name(Element party){return value(child(party,CAC,"PartyTaxScheme"),CBC,"RegistrationName");}
    private static String identification(Element party){var id=child(child(party,CAC,"PartyTaxScheme"),CBC,"CompanyID");return id.getTextContent()+(id.hasAttribute("schemeID")?"-"+id.getAttribute("schemeID"):"");}
    private static String address(Element party){var a=child(child(party,CAC,"PhysicalLocation"),CAC,"Address");return value(child(a,CAC,"AddressLine"),CBC,"Line")+", "+value(a,CBC,"CityName")+", "+value(a,CBC,"CountrySubentity")+", "+value(child(a,CAC,"Country"),CBC,"IdentificationCode");}
    private static String value(Element e,String ns,String local){var list=children(e,ns,local);return list.isEmpty()?"":list.getFirst().getTextContent();}
    private static Element child(Element e,String ns,String local){return DianDocumentIdentity.child(e,ns,local);}
    private static List<Element> children(Element e,String ns,String local){List<Element> list=new ArrayList<>();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element c&&ns.equals(c.getNamespaceURI())&&local.equals(c.getLocalName()))list.add(c);return list;}
}
