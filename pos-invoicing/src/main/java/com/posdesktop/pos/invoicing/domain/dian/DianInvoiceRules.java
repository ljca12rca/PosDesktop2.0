package com.posdesktop.pos.invoicing.domain.dian;

import com.posdesktop.pos.invoicing.domain.*;
import java.math.BigDecimal;
import java.util.Set;

/** Perfil acotado: venta nacional estandar COP, operacion 10, sin firma ni transporte. */
public final class DianInvoiceRules {
    private final DianCatalogs catalogs = new DianCatalogs();
    public void validate(FiscalInvoice invoice, DianProfile profile) {
        require("COP".equals(invoice.currency().getCurrencyCode()), "currency", "Solo COP en este perfil.");
        require(invoice.numbering().resolution().matches("[0-9]+"), "resolution", "Resolucion numerica requerida.");
        party(invoice.issuer()); party(invoice.customer());
        require("31".equals(invoice.issuer().identificationType()), "issuer", "El emisor debe identificarse con NIT.");
        require(!"1".equals(profile.customerOrganization()) || "31".equals(invoice.customer().identificationType()),
                "customer", "Persona juridica nacional requiere NIT.");
        catalogs.require("payment-means", invoice.payment().meansCode());
        if ("1".equals(profile.paymentForm())) require(invoice.payment().dueDate().equals(invoice.issuedAt().toLocalDate()),
                "payment", "Pago de contado requiere vencimiento en fecha de emision.");
        for (var adjustment : invoice.adjustments()) {
            catalogs.require("adjustment", adjustment.reasonCode());
            require(adjustment.charge() == Set.of("02", "03").contains(adjustment.reasonCode()),
                    "adjustment", "Codigo de motivo incompatible con cargo/descuento.");
        }
        for (var line : invoice.lines()) {
            catalogs.require("unit", line.unitCode());
            require(line.unitPrice().signum() > 0, "price", "Operaciones gratuitas requieren PricingReference; fuera de este perfil.");
            require(Math.max(0, line.unitPrice().stripTrailingZeros().scale()) <= 6
                    && Math.max(0, line.quantity().stripTrailingZeros().scale()) <= 6, "line", "Precio y cantidad admiten hasta seis decimales.");
            for (var tax : line.taxes()) {
                catalogs.require("tax", tax.schemeId());
                require(!Set.of("05", "06", "07").contains(tax.schemeId()), "tax", "Las retenciones van en WithholdingTaxTotal.");
                if (tax.treatment() == FiscalInvoice.Treatment.EXCLUDED) continue;
                if (tax.perUnit() != null) {
                    var unit = tax.perUnit();
                    catalogs.require("unit", unit.unitCode());
                    require(Set.of("22", "33", "34", "36").contains(tax.schemeId()), "tax", "Tributo por unidad no implementado en este perfil.");
                    BigDecimal divisor = "34".equals(tax.schemeId()) ? new BigDecimal("100") : BigDecimal.ONE;
                    require(unit.referenceQuantity().compareTo(divisor) == 0, "tax", "Divisor fiscal incorrecto.");
                    require(!"34".equals(tax.schemeId()) || "MLT".equals(unit.unitCode()), "tax", "IBUA requiere mililitros (MLT, catalogo 13.3.6).");
                    require(unit.quantity().scale() <= 2 && unit.rate().scale() <= 2, "tax", "Base fisica y tarifa admiten hasta dos decimales.");
                    if ("22".equals(tax.schemeId())) require(unit.quantity().compareTo(line.quantity()) == 0
                            && unit.unitCode().equals(line.unitCode()), "tax", "INCBolsas requiere la cantidad y unidad facturadas.");
                    if ("34".equals(tax.schemeId())) {
                        catalogs.require("ibua-" + invoice.issuedAt().getYear(), unit.rate().stripTrailingZeros().toPlainString());
                    }
                } else {
                    require(!Set.of("22", "33", "34", "36").contains(tax.schemeId()), "tax", "Tributo exige base fisica y tarifa por unidad.");
                    String table = switch (tax.schemeId()) { case "01" -> "vat-rate"; case "04" -> "inc-rate"; default -> null; };
                    if (table != null) catalogs.require(table, tax.percent().stripTrailingZeros().toPlainString());
                    if ("35".equals(tax.schemeId())) catalogs.require("icui-" + invoice.issuedAt().getYear(), tax.percent().stripTrailingZeros().toPlainString());
                }
            }
        }
        for (var withholding : invoice.withholdings()) {
            require(Set.of("05", "06", "07").contains(withholding.schemeId()), "withholding", "Codigo de retencion invalido.");
            if (!"07".equals(withholding.schemeId())) catalogs.require("05".equals(withholding.schemeId())
                    ? "rete-vat-rate" : "rete-income-rate", withholding.percent().stripTrailingZeros().toPlainString());
        }
        new InvoiceCalculator().calculate(invoice);
    }
    private void party(FiscalInvoice.Party party) {
        catalogs.require("identification", party.identificationType());
        require(party.identification().matches("[A-Za-z0-9]+"), "party", "Identificacion sin separadores ni DV.");
        if ("31".equals(party.identificationType())) require(NitCheckDigit.calculate(party.identification()).equals(party.checkDigit()),
                "party", "Digito de verificacion incorrecto.");
        for (String duty : party.taxResponsibilities().split(";", -1)) catalogs.require("responsibility", duty);
        catalogs.require("party-tax", party.taxSchemeId());
        var address = party.address();
        catalogs.require("country", address.countryCode());
        require("CO".equals(address.countryCode()), "address", "Perfil nacional: pais CO requerido.");
        catalogs.require("department", address.departmentCode());
        catalogs.require("municipality", address.cityCode());
        require(address.cityCode().startsWith(address.departmentCode()), "address", "Municipio no pertenece al departamento.");
    }
    private static void require(boolean condition, String field, String message) {
        if (!condition) throw new InvoiceValidationException(field, "DIAN_RULE", message);
    }
}
