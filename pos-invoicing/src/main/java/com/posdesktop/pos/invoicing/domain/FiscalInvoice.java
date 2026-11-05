package com.posdesktop.pos.invoicing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Snapshot inmutable: no referencias a entidades JPA, clientes vivos ni DTO de proveedores. */
public record FiscalInvoice(UUID sourceSaleId, String number, OffsetDateTime issuedAt, Currency currency,
        Party issuer, Party customer, Numbering numbering, Payment payment, List<Line> lines,
        BigDecimal declaredPayable, List<Adjustment> adjustments, List<Withholding> withholdings,
        BigDecimal payableRounding) {
    public FiscalInvoice(UUID sourceSaleId, String number, OffsetDateTime issuedAt, Currency currency,
            Party issuer, Party customer, Numbering numbering, Payment payment, List<Line> lines,
            BigDecimal declaredPayable) {
        this(sourceSaleId, number, issuedAt, currency, issuer, customer, numbering, payment, lines,
                declaredPayable, List.of(), List.of(), BigDecimal.ZERO);
    }
    public FiscalInvoice {
        adjustments = List.copyOf(Amounts.required(adjustments, "adjustments"));
        withholdings = List.copyOf(Amounts.required(withholdings, "withholdings"));
        payableRounding = Amounts.required(payableRounding, "payableRounding").setScale(2, java.math.RoundingMode.UNNECESSARY);
        Amounts.required(sourceSaleId, "sourceSaleId");
        number = Amounts.text(number, "number");
        Amounts.required(issuedAt, "issuedAt");
        Amounts.required(currency, "currency");
        Amounts.required(issuer, "issuer");
        Amounts.required(customer, "customer");
        Amounts.required(numbering, "numbering");
        Amounts.required(payment, "payment");
        lines = List.copyOf(Amounts.required(lines, "lines"));
        if (lines.isEmpty()) throw new InvoiceValidationException("lines", "REQUIRED", "Se requiere una linea.");
        declaredPayable = Amounts.money(Amounts.nonNegative(declaredPayable, "declaredPayable"));
    }

    public record Address(String street, String cityCode, String cityName, String departmentCode,
            String departmentName, String countryCode) {
        public Address {
            street = Amounts.text(street, "address.street");
            cityCode = Amounts.text(cityCode, "address.cityCode");
            cityName = Amounts.text(cityName, "address.cityName");
            departmentCode = Amounts.text(departmentCode, "address.departmentCode");
            departmentName = Amounts.text(departmentName, "address.departmentName");
            countryCode = Amounts.text(countryCode, "address.countryCode");
            if (!countryCode.matches("[A-Z]{2}")) throw new InvoiceValidationException(
                    "address.countryCode", "COUNTRY_FORMAT", "Se esperan dos letras mayusculas.");
        }
    }

    public record Party(String identificationType, String identification, String checkDigit,
            String name, String taxResponsibilities, String taxSchemeId, String taxSchemeName, Address address) {
        public Party {
            identificationType = Amounts.text(identificationType, "party.identificationType");
            identification = Amounts.text(identification, "party.identification");
            name = Amounts.text(name, "party.name");
            taxResponsibilities = Amounts.text(taxResponsibilities, "party.taxResponsibilities");
            taxSchemeId = Amounts.text(taxSchemeId, "party.taxSchemeId");
            taxSchemeName = Amounts.text(taxSchemeName, "party.taxSchemeName");
            Amounts.required(address, "party.address");
            // Validacion sintactica, NO se presenta como validacion del DV DIAN.
            if ("31".equals(identificationType) && (!identification.matches("[0-9]+")
                    || checkDigit == null || !checkDigit.matches("[0-9]"))) {
                throw new InvoiceValidationException("party.identification", "NIT_FORMAT", "NIT numerico y DV de un digito requeridos.");
            }
        }
    }

    public enum Treatment { TAXED, EXEMPT, EXCLUDED }

    public record Tax(Treatment treatment, String schemeId, String schemeName, BigDecimal percent,
            UnitTax perUnit) {
        public Tax(Treatment treatment, String schemeId, String schemeName, BigDecimal percent) {
            this(treatment, schemeId, schemeName, percent, null);
        }
        public Tax {
            Amounts.required(treatment, "tax.treatment");
            schemeId = Amounts.text(schemeId, "tax.schemeId");
            schemeName = Amounts.text(schemeName, "tax.schemeName");
            percent = Amounts.nonNegative(percent, "tax.percent");
            percent = percent.stripTrailingZeros();
            if (perUnit != null && (treatment != Treatment.TAXED || percent.signum() != 0)) {
                throw new InvoiceValidationException("tax", "UNIT_TAX", "Tributo por unidad requiere tratamiento gravado sin porcentaje.");
            }
            if (perUnit == null && (treatment == Treatment.TAXED) != (percent.signum() > 0)) {
                throw new InvoiceValidationException("tax.percent", "TREATMENT_RATE", "Gravado requiere tasa positiva; exento/excluido, cero.");
            }
        }
    }

    /** Cantidad fisica total explicita; p.ej. IBUA: mililitros / 100 * tarifa. */
    public record UnitTax(BigDecimal quantity, String unitCode, BigDecimal referenceQuantity, BigDecimal rate) {
        public UnitTax {
            quantity = Amounts.nonNegative(quantity, "unitTax.quantity");
            unitCode = Amounts.text(unitCode, "unitTax.unitCode");
            referenceQuantity = Amounts.nonNegative(referenceQuantity, "unitTax.referenceQuantity");
            rate = Amounts.nonNegative(rate, "unitTax.rate");
            if (quantity.signum() == 0 || referenceQuantity.signum() == 0)
                throw new InvoiceValidationException("unitTax", "POSITIVE", "Cantidad y referencia deben ser positivas; tarifa no negativa.");
        }
    }

    /** Ajuste financiero global: no redistribuye bases gravables de las lineas (FAU14). */
    public record Adjustment(boolean charge, String reasonCode, String reason, BigDecimal amount) {
        public Adjustment {
            reasonCode = Amounts.text(reasonCode, "adjustment.reasonCode");
            reason = Amounts.text(reason, "adjustment.reason");
            amount = Amounts.money(Amounts.nonNegative(amount, "adjustment.amount"));
        }
    }

    /** Retenciones informativas, no se restan de LegalMonetaryTotal (anexo 11.9.1). */
    public record Withholding(String schemeId, String schemeName, BigDecimal base, BigDecimal percent) {
        public Withholding {
            schemeId = Amounts.text(schemeId, "withholding.schemeId");
            schemeName = Amounts.text(schemeName, "withholding.schemeName");
            base = Amounts.money(Amounts.nonNegative(base, "withholding.base"));
            percent = Amounts.nonNegative(percent, "withholding.percent");
            if (percent.compareTo(new BigDecimal("100")) > 0)
                throw new InvoiceValidationException("withholding.percent", "PERCENT", "Retencion no puede superar el 100%.");
        }
        public BigDecimal amount() { return Amounts.money(base.multiply(percent).movePointLeft(2)); }
    }

    public record Line(String reference, String description, BigDecimal quantity, String unitCode,
            BigDecimal unitPrice, BigDecimal discount, BigDecimal charge, List<Tax> taxes) {
        public Line {
            reference = Amounts.text(reference, "line.reference");
            description = Amounts.text(description, "line.description");
            unitCode = Amounts.text(unitCode, "line.unitCode");
            quantity = Amounts.nonNegative(quantity, "line.quantity");
            if (quantity.signum() == 0) throw new InvoiceValidationException("line.quantity", "POSITIVE", "Cantidad mayor que cero.");
            unitPrice = Amounts.nonNegative(unitPrice, "line.unitPrice");
            discount = Amounts.money(Amounts.nonNegative(discount, "line.discount"));
            charge = Amounts.money(Amounts.nonNegative(charge, "line.charge"));
            taxes = List.copyOf(Amounts.required(taxes, "line.taxes"));
            if (taxes.isEmpty()) throw new InvoiceValidationException("line.taxes", "EXPLICIT_TREATMENT", "Indique gravado, exento o excluido explicitamente.");
            if (taxes.stream().anyMatch(t -> t.treatment() == Treatment.EXCLUDED) && taxes.size() != 1) {
                throw new InvoiceValidationException("line.taxes", "EXCLUDED_MIX", "No mezclar exclusion con otros tratamientos.");
            }
            if (taxes.stream().map(Tax::schemeId).distinct().count() != taxes.size()) {
                throw new InvoiceValidationException("line.taxes", "DUPLICATE", "No repetir tributo en la misma linea.");
            }
        }
    }

    /** Referencia suministrada por el llamador: nunca reserva consecutivos. */
    public record Numbering(String prefix, String resolution, LocalDate validFrom, LocalDate validUntil,
            long rangeFrom, long rangeUntil) {
        public Numbering {
            prefix = Amounts.required(prefix, "numbering.prefix");
            resolution = Amounts.text(resolution, "numbering.resolution");
            Amounts.required(validFrom, "numbering.validFrom");
            Amounts.required(validUntil, "numbering.validUntil");
            if (rangeFrom < 1 || rangeUntil < rangeFrom || validUntil.isBefore(validFrom)) {
                throw new InvoiceValidationException("numbering", "RANGE", "Rango o vigencia invalida.");
            }
        }
    }

    public record Payment(String meansCode, LocalDate dueDate, String reference) {
        public Payment {
            meansCode = Amounts.text(meansCode, "payment.meansCode");
            Amounts.required(dueDate, "payment.dueDate");
            reference = Amounts.text(reference, "payment.reference");
        }
    }
}
