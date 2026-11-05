package com.posdesktop.pos.invoicing;

import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.domain.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceCalculatorTest {
    private final InvoiceCalculator calculator = new InvoiceCalculator();
    @Test void iva19() {
        var totals = calculator.calculate(InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED)), "119"));
        assertEquals(new BigDecimal("19.00"), totals.tax());
        assertEquals(new BigDecimal("119.00"), totals.payable());
    }
    @Test void iva5() {
        var totals = calculator.calculate(InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "5", FiscalInvoice.Treatment.TAXED)), "105"));
        assertEquals(new BigDecimal("5.00"), totals.tax());
    }
    @Test void multiplesProductosYTratamientos() {
        var lines = List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED),
                InvoiceFixtures.line("100", "5", FiscalInvoice.Treatment.TAXED),
                InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXEMPT),
                InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED));
        var totals = calculator.calculate(InvoiceFixtures.invoice(lines, "424"));
        assertEquals(4, totals.lines().size());
        assertEquals(new BigDecimal("24.00"), totals.tax());
        assertEquals(1, totals.lines().get(2).taxes().size());
        assertTrue(totals.lines().get(3).taxes().isEmpty());
    }
    @Test void descuentosCargosYRedondeoPorLinea() {
        var original = InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED);
        var adjusted = new FiscalInvoice.Line("1", "Con descuento", BigDecimal.ONE, "94", new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("5"), original.taxes());
        assertEquals(new BigDecimal("113.05"), calculator.calculate(InvoiceFixtures.invoice(List.of(adjusted), "113.05")).payable());
        var fractional = new FiscalInvoice.Line("2", "Fraccion", new BigDecimal("3"), "94", new BigDecimal("0.335"),
                BigDecimal.ZERO, BigDecimal.ZERO, original.taxes());
        assertEquals(new BigDecimal("1.19"), calculator.calculate(InvoiceFixtures.invoice(List.of(fractional), "1.19")).payable());
    }
    @Test void rechazaTotalDiferente() {
        var error = assertThrows(InvoiceValidationException.class, () -> calculator.calculate(
                InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED)), "100")));
        assertEquals("TOTAL_MISMATCH", error.rule());
    }
    @Test void noInfiereTratamientoNiAceptaTasaIncompatible() {
        assertThrows(InvoiceValidationException.class, () -> new FiscalInvoice.Tax(FiscalInvoice.Treatment.EXEMPT, "01", "IVA", BigDecimal.ONE));
        assertThrows(InvoiceValidationException.class, () -> new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED, "01", "IVA", BigDecimal.ZERO));
    }
    @Test void snapshotNoComparteColeccionesMutables() {
        var list = new ArrayList<>(List.of(InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED)));
        var invoice = InvoiceFixtures.invoice(list, "100");
        list.clear();
        assertEquals(1, invoice.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> invoice.lines().clear());
    }
    @Test void nitMalFormadoYCampoObligatorio() {
        var p = InvoiceFixtures.party();
        assertThrows(InvoiceValidationException.class, () -> new FiscalInvoice.Party("31", "NO-NIT", "X", p.name(),
                p.taxResponsibilities(), p.taxSchemeId(), p.taxSchemeName(), p.address()));
        assertThrows(InvoiceValidationException.class, () -> InvoiceFixtures.line("-1", "0", FiscalInvoice.Treatment.EXCLUDED));
        assertThrows(InvoiceValidationException.class, () -> new FiscalInvoice.Payment(" ", java.time.LocalDate.now(), "x"));
    }
    @Test void fechaOffsetYVencimientoInvalidos() {
        var base = InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED)), "100");
        var wrongOffset = new FiscalInvoice(base.sourceSaleId(), base.number(), base.issuedAt().withOffsetSameLocal(java.time.ZoneOffset.UTC),
                base.currency(), base.issuer(), base.customer(), base.numbering(), base.payment(), base.lines(), base.declaredPayable());
        assertEquals("BOGOTA_OFFSET", assertThrows(InvoiceValidationException.class, () -> calculator.calculate(wrongOffset)).rule());
        var overdue = new FiscalInvoice(base.sourceSaleId(), base.number(), base.issuedAt(), base.currency(), base.issuer(), base.customer(),
                base.numbering(), new FiscalInvoice.Payment("10",java.time.LocalDate.of(2026,1,1),"pago"), base.lines(), base.declaredPayable());
        assertEquals("DUE_DATE", assertThrows(InvoiceValidationException.class, () -> calculator.calculate(overdue)).rule());
    }
    @Test void numeracionInvalidaNoSeNormalizaNiReserva() {
        var base = InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED)), "100");
        for (String number : List.of("PRUEBA101", "PRUEBA+1", "PRUEBA", "PRUEBA-1")) {
            var invoice = new FiscalInvoice(base.sourceSaleId(), number, base.issuedAt(), base.currency(), base.issuer(), base.customer(),
                    base.numbering(), base.payment(), base.lines(), base.declaredPayable());
            assertEquals("AUTHORIZED_RANGE", assertThrows(InvoiceValidationException.class, () -> calculator.calculate(invoice)).rule());
        }
    }
}
