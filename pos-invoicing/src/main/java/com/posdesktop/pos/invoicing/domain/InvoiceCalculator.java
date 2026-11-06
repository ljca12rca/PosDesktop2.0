package com.posdesktop.pos.invoicing.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class InvoiceCalculator {
    public record TaxAmount(FiscalInvoice.Tax tax, BigDecimal base, BigDecimal amount) { }
    public record LineAmounts(FiscalInvoice.Line line, BigDecimal net, List<TaxAmount> taxes) {
        public LineAmounts { taxes = List.copyOf(taxes); }
    }
    public record Totals(List<LineAmounts> lines, BigDecimal net, BigDecimal tax, BigDecimal payable,
            BigDecimal taxableBase, BigDecimal inclusive, BigDecimal allowances, BigDecimal charges,
            BigDecimal withholding) {
        public Totals { lines = List.copyOf(lines); }
    }

    public Totals calculate(FiscalInvoice invoice) {
        if (!invoice.issuedAt().getOffset().equals(java.time.ZoneOffset.ofHours(-5))) {
            throw new InvoiceValidationException("issuedAt", "BOGOTA_OFFSET", "La fecha local debe conservar offset -05:00.");
        }
        var date = invoice.issuedAt().toLocalDate();
        if (date.isBefore(invoice.numbering().validFrom()) || date.isAfter(invoice.numbering().validUntil())) {
            throw new InvoiceValidationException("numbering", "VALIDITY", "Fecha fuera de vigencia.");
        }
        if (!invoice.number().startsWith(invoice.numbering().prefix())) {
            throw new InvoiceValidationException("number", "PREFIX", "El numero no contiene el prefijo indicado.");
        }
        try {
            String sequence = invoice.number().substring(invoice.numbering().prefix().length());
            if (!sequence.matches("[0-9]+")) throw new NumberFormatException();
            long number = Long.parseLong(sequence);
            if (number < invoice.numbering().rangeFrom() || number > invoice.numbering().rangeUntil()) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            throw new InvoiceValidationException("number", "AUTHORIZED_RANGE", "Consecutivo fuera de rango o no numerico.");
        }
        if (invoice.payment().dueDate().isBefore(date)) {
            throw new InvoiceValidationException("payment.dueDate", "DUE_DATE", "Vencimiento anterior a emision.");
        }
        List<LineAmounts> lines = new ArrayList<>();
        BigDecimal netTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (var line : invoice.lines()) {
            var calculated=calculateLine(line);
            lines.add(calculated);netTotal=netTotal.add(calculated.net());
            taxTotal=taxTotal.add(calculated.taxes().stream().map(TaxAmount::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
        }
        return finish(invoice,lines,netTotal,taxTotal);
    }

    public LineAmounts calculateLine(FiscalInvoice.Line line) {
            BigDecimal gross = Amounts.money(line.quantity().multiply(line.unitPrice()));
            if (line.discount().compareTo(gross) > 0) throw new InvoiceValidationException(
                    "line.discount", "DISCOUNT_BASE", "Descuento superior al importe bruto.");
            BigDecimal net = Amounts.money(gross.subtract(line.discount()).add(line.charge()));
            List<TaxAmount> taxes = new ArrayList<>();
            for (var tax : line.taxes()) {
                if (tax.treatment() == FiscalInvoice.Treatment.EXCLUDED) continue;
                BigDecimal amount = tax.perUnit() == null
                        ? Amounts.money(net.multiply(tax.percent()).movePointLeft(2))
                        : tax.perUnit().quantity().multiply(tax.perUnit().rate())
                            .divide(tax.perUnit().referenceQuantity(), Amounts.SCALE, Amounts.ROUNDING);
                taxes.add(new TaxAmount(tax, net, amount));
            }
            return new LineAmounts(line,net,List.copyOf(taxes));
    }

    private Totals finish(FiscalInvoice invoice,List<LineAmounts> lines,BigDecimal netTotal,BigDecimal taxTotal) {
        BigDecimal allowances = invoice.adjustments().stream().filter(a -> !a.charge()).map(FiscalInvoice.Adjustment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charges = invoice.adjustments().stream().filter(FiscalInvoice.Adjustment::charge).map(FiscalInvoice.Adjustment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inclusive = Amounts.money(netTotal.add(taxTotal));
        BigDecimal payable = Amounts.money(inclusive.subtract(allowances).add(charges).add(invoice.payableRounding()));
        if (payable.signum() < 0) throw new InvoiceValidationException("adjustments", "NEGATIVE_TOTAL", "Los ajustes generan un total negativo.");
        BigDecimal base = lines.stream().filter(l -> !l.taxes().isEmpty()).map(LineAmounts::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withheld = invoice.withholdings().stream().map(FiscalInvoice.Withholding::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Totals totals = new Totals(lines, Amounts.money(netTotal), Amounts.money(taxTotal), payable,
                Amounts.money(base), inclusive, Amounts.money(allowances), Amounts.money(charges), Amounts.money(withheld));
        if (totals.payable().compareTo(invoice.declaredPayable()) != 0) {
            throw new InvoiceValidationException("declaredPayable", "TOTAL_MISMATCH", "El total comercial no coincide con las lineas e impuestos.");
        }
        return totals;
    }
}
