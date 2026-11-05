package com.posdesktop.pos.invoicing.infrastructure.xml;

import com.posdesktop.pos.invoicing.application.ElectronicProvider;
import com.posdesktop.pos.invoicing.application.GenerateInvoiceDraft;
import com.posdesktop.pos.invoicing.application.ProviderType;
import com.posdesktop.pos.invoicing.domain.Amounts;
import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import com.posdesktop.pos.invoicing.domain.InvoiceCalculator;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** UBL 2.1 de desarrollo. NO implementa perfil DIAN completo, CUFE ni firma. */
public final class UblDraftGenerator implements GenerateInvoiceDraft, ElectronicProvider {
    public static final String INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private final InvoiceCalculator calculator = new InvoiceCalculator();

    @Override public ProviderType type() { return ProviderType.DIAN_DIRECT; }
    @Override public Set<Capability> capabilities() { return Set.of(Capability.LOCAL_UBL_DRAFT); }

    @Override public Draft generate(FiscalInvoice invoice) {
        var totals = calculator.calculate(invoice);
        try {
            StringWriter text = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(text);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("", "Invoice", INVOICE);
            xml.writeDefaultNamespace(INVOICE);
            xml.writeNamespace("cbc", CBC);
            xml.writeNamespace("cac", CAC);
            basic(xml, "UBLVersionID", "2.1");
            basic(xml, "ProfileID", "POSDESKTOP: BORRADOR UBL SIN FIRMA NI VALIDACION DIAN");
            basic(xml, "ID", invoice.number());
            basic(xml, "IssueDate", invoice.issuedAt().toLocalDate().toString());
            basic(xml, "IssueTime", invoice.issuedAt().format(DateTimeFormatter.ofPattern("HH:mm:ssXXX")));
            basic(xml, "Note", "BORRADOR LOCAL. NO ES FACTURA EXPEDIDA. NO TRANSMITIR.");
            basic(xml, "DocumentCurrencyCode", invoice.currency().getCurrencyCode());
            basic(xml, "LineCountNumeric", Integer.toString(invoice.lines().size()));
            party(xml, "AccountingSupplierParty", invoice.issuer());
            party(xml, "AccountingCustomerParty", invoice.customer());
            aggregate(xml, "PaymentMeans");
            basic(xml, "PaymentMeansCode", invoice.payment().meansCode());
            basic(xml, "PaymentDueDate", invoice.payment().dueDate().toString());
            basic(xml, "PaymentID", invoice.payment().reference());
            xml.writeEndElement();
            for (var adjustment : invoice.adjustments()) {
                aggregate(xml, "AllowanceCharge");
                basic(xml, "ChargeIndicator", Boolean.toString(adjustment.charge()));
                basic(xml, "AllowanceChargeReasonCode", adjustment.reasonCode());
                basic(xml, "AllowanceChargeReason", adjustment.reason());
                money(xml, "Amount", adjustment.amount(), invoice);
                xml.writeEndElement();
            }
            taxTotals(xml, totals.lines().stream().flatMap(l -> l.taxes().stream()).toList(), invoice);
            Map<String, List<FiscalInvoice.Withholding>> withheldByScheme = new LinkedHashMap<>();
            for (var withholding : invoice.withholdings())
                withheldByScheme.computeIfAbsent(withholding.schemeId(), ignored -> new java.util.ArrayList<>()).add(withholding);
            for (var withholdings : withheldByScheme.values()) {
                aggregate(xml, "WithholdingTaxTotal");
                money(xml, "TaxAmount", withholdings.stream().map(FiscalInvoice.Withholding::amount).reduce(BigDecimal.ZERO, BigDecimal::add), invoice);
                for (var withholding : withholdings) {
                    aggregate(xml, "TaxSubtotal");
                    money(xml, "TaxableAmount", withholding.base(), invoice);
                    money(xml, "TaxAmount", withholding.amount(), invoice);
                    aggregate(xml, "TaxCategory");
                    basic(xml, "Percent", withholding.percent().toPlainString());
                    scheme(xml, withholding.schemeId(), withholding.schemeName());
                    xml.writeEndElement();
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }
            aggregate(xml, "LegalMonetaryTotal");
            money(xml, "LineExtensionAmount", totals.net(), invoice);
            money(xml, "TaxExclusiveAmount", totals.taxableBase(), invoice);
            money(xml, "TaxInclusiveAmount", totals.inclusive(), invoice);
            if (totals.allowances().signum() != 0) money(xml, "AllowanceTotalAmount", totals.allowances(), invoice);
            if (totals.charges().signum() != 0) money(xml, "ChargeTotalAmount", totals.charges(), invoice);
            if (invoice.payableRounding().signum() != 0) money(xml, "PayableRoundingAmount", invoice.payableRounding(), invoice);
            money(xml, "PayableAmount", totals.payable(), invoice);
            xml.writeEndElement();
            for (int index = 0; index < totals.lines().size(); index++) {
                line(xml, index + 1, totals.lines().get(index), invoice);
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return new Draft(text.toString(), type(), Draft.State.LOCAL_DRAFT_NOT_ISSUED);
        } catch (XMLStreamException error) {
            throw new IllegalStateException("No se pudo serializar el borrador UBL.", error);
        }
    }

    private void party(XMLStreamWriter xml, String role, FiscalInvoice.Party party) throws XMLStreamException {
        aggregate(xml, role);
        aggregate(xml, "Party");
        aggregate(xml, "PartyIdentification");
        basic(xml, "ID", party.identification());
        xml.writeEndElement();
        aggregate(xml, "PartyName");
        basic(xml, "Name", party.name());
        xml.writeEndElement();
        aggregate(xml, "PostalAddress");
        var address = party.address();
        basic(xml, "ID", address.cityCode());
        basic(xml, "CityName", address.cityName());
        basic(xml, "CountrySubentity", address.departmentName());
        basic(xml, "CountrySubentityCode", address.departmentCode());
        aggregate(xml, "AddressLine");
        basic(xml, "Line", address.street());
        xml.writeEndElement();
        aggregate(xml, "Country");
        basic(xml, "IdentificationCode", address.countryCode());
        xml.writeEndElement();
        xml.writeEndElement();
        aggregate(xml, "PartyTaxScheme");
        basic(xml, "RegistrationName", party.name());
        xml.writeStartElement("cbc", "CompanyID", CBC);
        xml.writeAttribute("schemeName", party.identificationType());
        if (party.checkDigit() != null) xml.writeAttribute("schemeID", party.checkDigit());
        xml.writeCharacters(party.identification());
        xml.writeEndElement();
        basic(xml, "TaxLevelCode", party.taxResponsibilities());
        scheme(xml, party.taxSchemeId(), party.taxSchemeName());
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void line(XMLStreamWriter xml, int index, InvoiceCalculator.LineAmounts amounts,
            FiscalInvoice invoice) throws XMLStreamException {
        var line = amounts.line();
        aggregate(xml, "InvoiceLine");
        basic(xml, "ID", Integer.toString(index));
        basic(xml, "Note", "Tratamiento local: " + line.taxes().stream().map(t -> t.treatment().name()).distinct().toList());
        xml.writeStartElement("cbc", "InvoicedQuantity", CBC);
        xml.writeAttribute("unitCode", line.unitCode());
        xml.writeCharacters(line.quantity().toPlainString());
        xml.writeEndElement();
        money(xml, "LineExtensionAmount", amounts.net(), invoice);
        adjustment(xml, line.discount(), false, invoice);
        adjustment(xml, line.charge(), true, invoice);
        taxTotals(xml, amounts.taxes(), invoice);
        aggregate(xml, "Item");
        basic(xml, "Description", line.description());
        aggregate(xml, "SellersItemIdentification");
        basic(xml, "ID", line.reference());
        xml.writeEndElement();
        xml.writeEndElement();
        aggregate(xml, "Price");
        // El precio unitario conserva precision; los importes monetarios se redondean en el calculador.
        xml.writeStartElement("cbc", "PriceAmount", CBC);
        xml.writeAttribute("currencyID", invoice.currency().getCurrencyCode());
        xml.writeCharacters(line.unitPrice().toPlainString());
        xml.writeEndElement();
        xml.writeStartElement("cbc", "BaseQuantity", CBC);
        xml.writeAttribute("unitCode", line.unitCode());
        xml.writeCharacters("1");
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void adjustment(XMLStreamWriter xml, BigDecimal amount, boolean charge, FiscalInvoice invoice) throws XMLStreamException {
        if (amount.signum() == 0) return;
        aggregate(xml, "AllowanceCharge");
        basic(xml, "ChargeIndicator", Boolean.toString(charge));
        money(xml, "Amount", amount, invoice);
        xml.writeEndElement();
    }

    private void taxTotals(XMLStreamWriter xml, List<InvoiceCalculator.TaxAmount> taxes,
            FiscalInvoice invoice) throws XMLStreamException {
        if (taxes.isEmpty()) return; // Excluido no se disfraza de exento (tasa cero).
        Map<String, List<InvoiceCalculator.TaxAmount>> schemes = new LinkedHashMap<>();
        for (var tax : taxes) schemes.computeIfAbsent(tax.tax().schemeId(), ignored -> new java.util.ArrayList<>()).add(tax);
        for (var values : schemes.values()) taxTotal(xml, values, invoice);
    }

    private void taxTotal(XMLStreamWriter xml, List<InvoiceCalculator.TaxAmount> taxes,
            FiscalInvoice invoice) throws XMLStreamException {
        Map<FiscalInvoice.Tax, BigDecimal[]> grouped = new LinkedHashMap<>();
        for (var tax : taxes) {
            var amounts = grouped.computeIfAbsent(tax.tax(), ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            amounts[0] = amounts[0].add(tax.base());
            amounts[1] = amounts[1].add(tax.amount());
        }
        aggregate(xml, "TaxTotal");
        money(xml, "TaxAmount", taxes.stream().map(InvoiceCalculator.TaxAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add), invoice);
        for (var entry : grouped.entrySet()) {
            aggregate(xml, "TaxSubtotal");
            if (entry.getKey().perUnit() == null) money(xml, "TaxableAmount", entry.getValue()[0], invoice);
            money(xml, "TaxAmount", entry.getValue()[1], invoice);
            var unit = entry.getKey().perUnit();
            if (unit != null) {
                xml.writeStartElement("cbc", "BaseUnitMeasure", CBC);
                xml.writeAttribute("unitCode", unit.unitCode());
                BigDecimal quantity = taxes.stream().filter(t -> t.tax().equals(entry.getKey()))
                        .map(t -> t.tax().perUnit().quantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
                xml.writeCharacters(quantity.toPlainString());
                xml.writeEndElement();
                money(xml, "PerUnitAmount", unit.rate(), invoice);
            }
            aggregate(xml, "TaxCategory");
            if (unit == null) basic(xml, "Percent", entry.getKey().percent().toPlainString());
            scheme(xml, entry.getKey().schemeId(), entry.getKey().schemeName());
            xml.writeEndElement();
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void scheme(XMLStreamWriter xml, String id, String name) throws XMLStreamException {
        aggregate(xml, "TaxScheme");
        basic(xml, "ID", id);
        basic(xml, "Name", name);
        xml.writeEndElement();
    }

    private void aggregate(XMLStreamWriter xml, String name) throws XMLStreamException {
        xml.writeStartElement("cac", name, CAC);
    }

    private void basic(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement("cbc", name, CBC);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private void money(XMLStreamWriter xml, String name, BigDecimal amount, FiscalInvoice invoice) throws XMLStreamException {
        xml.writeStartElement("cbc", name, CBC);
        xml.writeAttribute("currencyID", invoice.currency().getCurrencyCode());
        xml.writeCharacters(Amounts.money(amount).toPlainString());
        xml.writeEndElement();
    }
}
