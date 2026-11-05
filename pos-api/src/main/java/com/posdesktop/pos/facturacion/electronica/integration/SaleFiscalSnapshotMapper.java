package com.posdesktop.pos.facturacion.electronica.integration;

import com.posdesktop.pos.invoicing.domain.Amounts;
import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import com.posdesktop.pos.invoicing.domain.InvoiceCalculator;
import com.posdesktop.pos.invoicing.domain.InvoiceValidationException;
import com.posdesktop.pos.modelo.relacional.Venta;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Lectura pura. No guarda documentos, no reserva numeros y no adivina impuestos. */
@Component
public final class SaleFiscalSnapshotMapper {
    public record Classification(String unitCode, List<FiscalInvoice.Tax> taxes) {
        public Classification { taxes = List.copyOf(taxes); }
    }
    public record Context(String number, FiscalInvoice.Party issuer, FiscalInvoice.Party customer,
            FiscalInvoice.Numbering numbering, FiscalInvoice.Payment payment,
            Map<Integer, Classification> byLineOrder, List<FiscalInvoice.Adjustment> adjustments,
            List<FiscalInvoice.Withholding> withholdings) {
        public Context(String number, FiscalInvoice.Party issuer, FiscalInvoice.Party customer,
                FiscalInvoice.Numbering numbering, FiscalInvoice.Payment payment, Map<Integer, Classification> byLineOrder) {
            this(number, issuer, customer, numbering, payment, byLineOrder, List.of(), List.of());
        }
        public Context {
            byLineOrder = Map.copyOf(byLineOrder);
            adjustments = List.copyOf(adjustments); withholdings = List.copyOf(withholdings);
        }
    }

    public FiscalInvoice snapshot(Venta sale, Context context) {
        BigDecimal detailDiscounts = sale.getDetalles().stream().map(d -> d.getDescuento()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal globalDiscounts = context.adjustments().stream().filter(a -> !a.charge())
                .map(FiscalInvoice.Adjustment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (detailDiscounts.add(globalDiscounts).compareTo(sale.getDescuentoTotal()) != 0) {
            throw new InvoiceValidationException("sale.discount", "GLOBAL_DISCOUNT_MISMATCH",
                    "Descuentos comerciales y fiscales no coinciden; indique los ajustes explicitamente.");
        }
        var lines = sale.getDetalles().stream().map(detail -> {
            var classification = context.byLineOrder().get(detail.getOrden());
            if (classification == null) throw new InvoiceValidationException("sale.lines", "FISCAL_CLASSIFICATION_REQUIRED",
                    "Cada linea necesita unidad y tratamiento tributario explicitos.");
            return new FiscalInvoice.Line(detail.getId().toString(), detail.getDescripcion(), detail.getCantidad(),
                    classification.unitCode(), detail.getPrecioUnitario(), detail.getDescuento(), BigDecimal.ZERO, classification.taxes());
        }).toList();
        var snapshot = new FiscalInvoice(sale.getId(), context.number(), sale.getFechaVenta().atZone(ZoneId.of("America/Bogota")).toOffsetDateTime(),
                java.util.Currency.getInstance("COP"), context.issuer(), context.customer(), context.numbering(), context.payment(), lines,
                sale.getTotal(), context.adjustments(), context.withholdings(), BigDecimal.ZERO);
        var totals = new InvoiceCalculator().calculate(snapshot);
        for (int index = 0; index < totals.lines().size(); index++) {
            var calculated = totals.lines().get(index);
            var commercial = sale.getDetalles().get(index);
            BigDecimal taxes = calculated.taxes().stream().map(InvoiceCalculator.TaxAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (taxes.compareTo(Amounts.money(commercial.getImpuesto())) != 0
                    || calculated.net().add(taxes).compareTo(Amounts.money(commercial.getTotal())) != 0) {
                throw new InvoiceValidationException("sale.lines[" + index + "]", "LINE_TOTAL_MISMATCH",
                        "Total o impuestos de la linea no coinciden con la venta.");
            }
        }
        if (totals.tax().compareTo(Amounts.money(sale.getImpuestoTotal())) != 0) {
            throw new InvoiceValidationException("sale.tax", "TAX_MISMATCH", "Impuestos comerciales y fiscales no coinciden.");
        }
        return snapshot;
    }
}
