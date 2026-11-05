package com.posdesktop.pos.invoicing.infrastructure.xml;

import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import java.io.*;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Generacion offline del perfil nacional; no implementa firma, transmision ni reserva de numeros. */
public final class DianInvoiceDraftGenerator {
    public static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    public static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    public static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    public static final String STS = "dian:gov:co:facturaelectronica:Structures-2-1";
    private static final String AGENCY = "CO, DIAN (Dirección de Impuestos y Aduanas Nacionales)";
    private static final OfflineUblValidator XSD = OfflineUblValidator.dian19();
    public record Draft(String xml, String cufe, List<String> pending) {
        public Draft { pending = List.copyOf(pending); }
        public boolean readyToTransmit() { return false; }
    }

    public Draft generate(FiscalInvoice invoice, DianProfile profile, DianSecrets secrets) {
        new DianInvoiceRules().validate(invoice, profile);
        var totals = new InvoiceCalculator().calculate(invoice);
        String cufe = DianIdentifiers.cufe(invoice, totals, profile, secrets);
        String security = DianIdentifiers.softwareSecurityCode(profile.softwareId().toString(), invoice.number(), secrets);
        try {
            Document doc = parse(new UblDraftGenerator().generate(invoice).xml());
            Element root = doc.getDocumentElement();
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ext", EXT);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:sts", STS);
            Element extensions = element(doc, "ext:UBLExtensions", null);
            root.insertBefore(extensions, root.getFirstChild());
            Element data = append(append(append(extensions, "ext:UBLExtension", null), "ext:ExtensionContent", null), "sts:DianExtensions", null);
            Element control = append(data, "sts:InvoiceControl", null);
            var numbering = invoice.numbering();
            append(control, "sts:InvoiceAuthorization", numbering.resolution());
            Element period = append(control, "sts:AuthorizationPeriod", null);
            append(period, "cbc:StartDate", numbering.validFrom().toString());
            append(period, "cbc:EndDate", numbering.validUntil().toString());
            Element authorized = append(control, "sts:AuthorizedInvoices", null);
            append(authorized, "sts:Prefix", numbering.prefix());
            append(authorized, "sts:From", Long.toString(numbering.rangeFrom()));
            append(authorized, "sts:To", Long.toString(numbering.rangeUntil()));
            Element country = append(append(data, "sts:InvoiceSource", null), "cbc:IdentificationCode", "CO");
            country.setAttribute("listAgencyID", "6");
            country.setAttribute("listAgencyName", "United Nations Economic Commission for Europe");
            country.setAttribute("listSchemeURI", "urn:oasis:names:specification:ubl:codelist:gc:CountryIdentificationCode-2.1");
            Element provider = append(data, "sts:SoftwareProvider", null);
            identification(append(provider, "sts:ProviderID", profile.providerNit()), "31", profile.providerDv());
            agency(append(provider, "sts:SoftwareID", profile.softwareId().toString()));
            agency(append(data, "sts:SoftwareSecurityCode", security));
            identification(append(append(data, "sts:AuthorizationProvider", null), "sts:AuthorizationProviderID", "800197268"), "31", "4");
            append(data, "sts:QRCode", profile.environment().qr(cufe));

            child(root, CBC, "UBLVersionID").setTextContent("UBL 2.1");
            root.insertBefore(element(doc, "cbc:CustomizationID", "10"), child(root, CBC, "ProfileID"));
            child(root, CBC, "ProfileID").setTextContent("DIAN 2.1: Factura Electrónica de Venta");
            root.insertBefore(element(doc, "cbc:ProfileExecutionID", profile.environment().code()), child(root, CBC, "ID"));
            Element uuid = element(doc, "cbc:UUID", cufe);
            uuid.setAttribute("schemeID", profile.environment().code()); uuid.setAttribute("schemeName", "CUFE-SHA384");
            root.insertBefore(uuid, child(root, CBC, "IssueDate"));
            root.insertBefore(element(doc, "cbc:InvoiceTypeCode", "01"), child(root, CBC, "Note"));
            decorateParty(child(root, CAC, "AccountingSupplierParty"), invoice.issuer(), profile.issuerOrganization(), numbering.prefix());
            decorateParty(child(root, CAC, "AccountingCustomerParty"), invoice.customer(), profile.customerOrganization(), null);
            var schemes = doc.getElementsByTagNameNS(CAC, "TaxScheme");
            var catalogs = new DianCatalogs();
            for (int i = 0; i < schemes.getLength(); i++) {
                Element scheme = (Element) schemes.item(i);
                String table = "PartyTaxScheme".equals(scheme.getParentNode().getLocalName()) ? "party-tax" : "tax";
                child(scheme, CBC, "Name").setTextContent(catalogs.require(table, child(scheme, CBC, "ID").getTextContent()).replaceAll("\\s*\\*+$", ""));
            }
            Element payment = child(root, CAC, "PaymentMeans");
            payment.insertBefore(element(doc, "cbc:ID", profile.paymentForm()), payment.getFirstChild());
            // Un segundo Extension con ds:Signature solo se agregara al implementar firma real.
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty("encoding", "UTF-8");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(output));
            String xml = output.toString();
            var result = XSD.validate(xml);
            if (!result.structurallyValid()) throw new IllegalStateException("Borrador DIAN invalido: " + result.issues());
            return new Draft(xml, cufe, List.of("FIRMA_XADES", "HABILITACION_DIAN", "TRANSPORTE_DIAN"));
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("No se pudo generar el borrador DIAN.", error); }
    }

    private static void decorateParty(Element role, FiscalInvoice.Party value, String organization, String prefix) {
        role.insertBefore(element(role.getOwnerDocument(), "cbc:AdditionalAccountID", organization), role.getFirstChild());
        Element party = child(role, CAC, "Party");
        Element postal = child(party, CAC, "PostalAddress");
        var catalogs = new DianCatalogs();
        child(postal, CBC, "CityName").setTextContent(catalogs.require("municipality", value.address().cityCode()));
        child(postal, CBC, "CountrySubentity").setTextContent(catalogs.require("department", value.address().departmentCode()));
        append(child(postal, CAC, "Country"), "cbc:Name", catalogs.require("country", value.address().countryCode())).setAttribute("languageID", "es");
        Element location = element(role.getOwnerDocument(), "cac:PhysicalLocation", null);
        party.insertBefore(location, postal);
        Element address = (Element) role.getOwnerDocument().renameNode(postal, CAC, "cac:Address");
        location.appendChild(address);
        Element tax = child(party, CAC, "PartyTaxScheme");
        identification(child(tax, CBC, "CompanyID"), value.identificationType(), value.checkDigit());
        child(child(tax, CAC, "TaxScheme"), CBC, "Name").setTextContent(catalogs.require("party-tax", value.taxSchemeId()));
        Element registration = (Element) address.cloneNode(true);
        role.getOwnerDocument().renameNode(registration, CAC, "cac:RegistrationAddress");
        tax.insertBefore(registration, child(tax, CAC, "TaxScheme"));
        Element legal = append(party, "cac:PartyLegalEntity", null);
        append(legal, "cbc:RegistrationName", value.name());
        identification(append(legal, "cbc:CompanyID", value.identification()), value.identificationType(), value.checkDigit());
        if (prefix != null) append(append(legal, "cac:CorporateRegistrationScheme", null), "cbc:ID", prefix);
    }
    public static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
    private static void agency(Element element) { element.setAttribute("schemeAgencyID", "195"); element.setAttribute("schemeAgencyName", AGENCY); }
    private static void identification(Element element, String type, String dv) {
        agency(element); element.setAttribute("schemeName", type);
        if ("31".equals(type)) element.setAttribute("schemeID", dv);
        else element.removeAttribute("schemeID");
    }
    private static Element append(Element parent, String name, String value) {
        Element result = element(parent.getOwnerDocument(), name, value); parent.appendChild(result); return result;
    }
    private static Element element(Document doc, String name, String value) {
        String namespace = switch (name.split(":")[0]) { case "cbc" -> CBC; case "cac" -> CAC; case "ext" -> EXT; case "sts" -> STS; default -> throw new IllegalArgumentException(name); };
        Element result = doc.createElementNS(namespace, name); if (value != null) result.setTextContent(value); return result;
    }
    private static Element child(Element parent, String namespace, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element value && namespace.equals(value.getNamespaceURI()) && name.equals(value.getLocalName())) return value;
        throw new IllegalStateException("Elemento requerido ausente: " + name);
    }
}
