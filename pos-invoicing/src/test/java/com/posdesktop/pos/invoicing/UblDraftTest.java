package com.posdesktop.pos.invoicing;

import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.application.*;
import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import com.posdesktop.pos.invoicing.infrastructure.xml.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class UblDraftTest {
    private static final OfflineUblValidator VALIDATOR = new OfflineUblValidator();
    private final UblDraftGenerator generator = new UblDraftGenerator();

    @Test void generaFacturaConIvaValidableXsdYCaracteresEspeciales() {
        var draft = generator.generate(InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED)), "119"));
        assertEquals(GenerateInvoiceDraft.Draft.State.LOCAL_DRAFT_NOT_ISSUED, draft.state());
        assertTrue(draft.xml().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(draft.xml().contains("&amp;"));
        assertTrue(draft.xml().contains("&lt;especial&gt;"));
        assertTrue(draft.xml().contains("10:20:30-05:00"));
        assertFalse(draft.xml().contains("<cbc:UUID")); // No CUFE inventado.
        var result = VALIDATOR.validate(draft.xml());
        assertTrue(result.structurallyValid(), result.issues().toString());
    }
    @Test void multiplesLineasExentasExcluidasYTasas() {
        var invoice = InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED),
                InvoiceFixtures.line("100", "5", FiscalInvoice.Treatment.TAXED),
                InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXEMPT),
                InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED)), "424");
        var result = VALIDATOR.validate(generator.generate(invoice).xml());
        assertTrue(result.structurallyValid(), result.issues().toString());
    }
    @Test void xmlMalFormadoNamespaceEquivocadoYCamposObligatorios() {
        assertFalse(VALIDATOR.validate("<Invoice>").structurallyValid());
        assertFalse(VALIDATOR.validate("<Invoice xmlns='incorrecto'/>").structurallyValid());
        var xml = generator.generate(InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXEMPT)), "100")).xml();
        assertFalse(VALIDATOR.validate(xml.replace("<cbc:ID>PRUEBA1</cbc:ID>", "")).structurallyValid());
    }
    @Test void bloqueaDoctypeEntidadesExternasYEsquemasRemotos() {
        assertFalse(VALIDATOR.validate("<!DOCTYPE Invoice [<!ENTITY xxe SYSTEM 'file:///no-leer'>]><Invoice>&xxe;</Invoice>").structurallyValid());
        assertFalse(VALIDATOR.validate("<!DOCTYPE Invoice SYSTEM 'https://no-contactar.invalid/xxe'><Invoice/>").structurallyValid());
    }
    @Test void resultadoDeterminista() {
        var invoice = InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED)), "100");
        assertEquals(generator.generate(invoice).xml(), generator.generate(invoice).xml());
    }
    @Test void ejemploLocalGeneradoYValidado() throws Exception {
        var invoice = InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED)), "119");
        var draft = generator.generate(invoice);
        var result = VALIDATOR.validate(draft.xml());
        assertTrue(result.structurallyValid(), result.issues().toString());
        var directory = java.nio.file.Path.of("target", "examples");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("invoice-draft.xml"), draft.xml(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
