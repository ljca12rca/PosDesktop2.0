package com.posdesktop.pos.invoicing.infrastructure.transport;

import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.time.LocalDate;
import org.w3c.dom.*;

/** Datos de correlacion leidos del XML que realmente se empaqueta. */
public record DianDocumentIdentity(String root,String number,String key,String issuerNit,LocalDate date,String environment) {
    public static DianDocumentIdentity read(String xml){
        try{
            Element root=DianInvoiceDraftGenerator.parse(xml).getDocumentElement();
            if(!java.util.Set.of("Invoice","CreditNote","DebitNote").contains(root.getLocalName())||!("urn:oasis:names:specification:ubl:schema:xsd:"+root.getLocalName()+"-2").equals(root.getNamespaceURI()))throw new IllegalArgumentException();
            Element supplier=child(root,DianInvoiceDraftGenerator.CAC,"AccountingSupplierParty");
            String nit=child(child(child(supplier,DianInvoiceDraftGenerator.CAC,"Party"),DianInvoiceDraftGenerator.CAC,"PartyTaxScheme"),DianInvoiceDraftGenerator.CBC,"CompanyID").getTextContent();
            String key=value(root,"UUID"),number=value(root,"ID"),env=value(root,"ProfileExecutionID");
            if(!key.matches("[0-9a-f]{96}")||!nit.matches("[0-9]{1,10}")||number.isBlank()||number.length()>80||!java.util.Set.of("1","2").contains(env))throw new IllegalArgumentException();
            return new DianDocumentIdentity(root.getLocalName(),number,key,nit,LocalDate.parse(value(root,"IssueDate")),env);
        }catch(Exception e){throw new IllegalArgumentException("El XML no tiene una identidad fiscal DIAN verificable.");}
    }
    public String filePrefix(){return switch(root){case "Invoice"->"fv";case "CreditNote"->"nc";default->"nd";};}
    private static String value(Element e,String name){return child(e,DianInvoiceDraftGenerator.CBC,name).getTextContent();}
    public static Element child(Element e,String ns,String name){Element found=null;for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element c&&ns.equals(c.getNamespaceURI())&&name.equals(c.getLocalName())){if(found!=null)throw new IllegalArgumentException("Elemento duplicado.");found=c;}if(found==null)throw new IllegalArgumentException("Elemento requerido ausente: "+name);return found;}
}
