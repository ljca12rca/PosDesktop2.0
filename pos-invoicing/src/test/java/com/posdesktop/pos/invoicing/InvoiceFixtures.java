package com.posdesktop.pos.invoicing;

import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

final class InvoiceFixtures {
    static FiscalInvoice.Party party() {
        return new FiscalInvoice.Party("31", "900000001", "1", "EMPRESA FICTICIA & HIJOS <PRUEBA>",
                "R-99-PN", "01", "IVA", new FiscalInvoice.Address("CALLE PRUEBA 1", "05001", "Medellin",
                "05", "Antioquia", "CO"));
    }
    static FiscalInvoice.Line line(String price, String rate, FiscalInvoice.Treatment treatment) {
        return new FiscalInvoice.Line("PRUEBA-1", "Articulo & <especial> ñ", new BigDecimal("1"), "94",
                new BigDecimal(price), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new FiscalInvoice.Tax(treatment, "01", "IVA", new BigDecimal(rate))));
    }
    static FiscalInvoice invoice(List<FiscalInvoice.Line> lines, String total) {
        return new FiscalInvoice(UUID.fromString("11111111-1111-4111-8111-111111111111"), "PRUEBA1",
                OffsetDateTime.parse("2026-10-29T10:20:30-05:00"), Currency.getInstance("COP"), party(), party(),
                new FiscalInvoice.Numbering("PRUEBA", "SOLO-EJEMPLO", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), 1, 100),
                new FiscalInvoice.Payment("10", LocalDate.of(2026,10,29), "PAGO-PRUEBA"), lines, new BigDecimal(total));
    }
}
