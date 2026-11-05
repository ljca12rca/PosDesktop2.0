package com.posdesktop.pos.invoicing;

import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.w3c.dom.*;

class Dian19Test {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static final OfflineUblValidator XSD = OfflineUblValidator.dian19();
    private static DianProfile profile(DianProfile.Environment environment) {
        return new DianProfile(environment, UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "860324218", "1", "1", "2", "1");
    }
    private static DianSecrets secrets() { return new DianSecrets("CLAVE-FICTICIA-NO-VALIDA".toCharArray(), "12345".toCharArray()); }
    private static FiscalInvoice invoice(List<FiscalInvoice.Line> lines, String total,
            List<FiscalInvoice.Adjustment> adjustments, List<FiscalInvoice.Withholding> withholdings) {
        var original = InvoiceFixtures.invoice(lines, total);
        var party = InvoiceFixtures.party();
        var issuer = new FiscalInvoice.Party("31", "860324218", "1", party.name(), party.taxResponsibilities(), "01", "IVA", party.address());
        var customer = new FiscalInvoice.Party("13", "123456789", null, "CLIENTE FICTICIO", "R-99-PN", "ZZ", "No aplica", party.address());
        return new FiscalInvoice(original.sourceSaleId(), original.number(), original.issuedAt(), original.currency(), issuer, customer,
                new FiscalInvoice.Numbering("PRUEBA", "18760000001", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31), 1,100),
                original.payment(), lines, n(total), adjustments, withholdings, BigDecimal.ZERO);
    }
    private static FiscalInvoice base() { return invoice(List.of(InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED)), "119", List.of(), List.of()); }
    private static DianInvoiceDraftGenerator.Draft generate(FiscalInvoice invoice) {
        try (var secrets = secrets()) { return new DianInvoiceDraftGenerator().generate(invoice, profile(DianProfile.Environment.TEST), secrets); }
    }
    private static String value(Document doc, String namespace, String local) { return doc.getElementsByTagNameNS(namespace, local).item(0).getTextContent(); }

    @Test void cufeVectorOficialAnexo19Pagina657() {
        try (var secrets = new DianSecrets("693ff6f2a553c3646a063436fd4dd9ded0311471".toCharArray(), "pin-ficticio".toCharArray())) {
            var input = new DianIdentifiers.CufeInput("323200000129", "2019-01-16", "10:53:10-05:00",
                    n("1500000.00"), n("285000.00"), n("0"), n("0"), n("1785000.00"), "700085371", "800199436", "1");
            assertEquals("8bb918b19ba22a694f1da11c643b5e9de39adf60311cf179179e9b33381030bcd4c3c3f156c506ed5908f9276f5bd9b4", DianIdentifiers.cufe(input, secrets));
        }
    }
    @Test void seguridadSoftwareFormula118VectorSinteticoIndependiente() {
        try (var secrets = secrets()) {
            assertEquals("6332f76898c8595a5ba91da37a016d87b1abe756557bfe237e4e20fd08b07c190a92e7c4e274aafe712a04134c9f94e9",
                    DianIdentifiers.softwareSecurityCode("11111111-1111-4111-8111-111111111111", "PRUEBA1", secrets));
            assertFalse(secrets.toString().contains("12345"));
        }
        var closed = secrets(); closed.close();
        assertThrows(IllegalStateException.class, () -> DianIdentifiers.softwareSecurityCode("id", "n", closed));
    }
    @Test void dvEjemploOficialOrden41989YAutoridadDian() {
        assertEquals("1", NitCheckDigit.calculate("860324218"));
        assertEquals("4", NitCheckDigit.calculate("800197268"));
        assertThrows(InvoiceValidationException.class, () -> NitCheckDigit.calculate("800197268-4"));
        assertThrows(InvoiceValidationException.class, () -> NitCheckDigit.calculate("1234567890123456"));
    }
    @Test void catalogosOficialesIncluyenTarifasYRelacionDane() {
        var catalogs = new DianCatalogs();
        assertEquals("MEDELLÍN", catalogs.require("municipality", "05001"));
        assertEquals("Antioquia", catalogs.require("department", "05"));
        catalogs.require("vat-rate", "19"); catalogs.require("rete-vat-rate", "100");
        catalogs.require("unit", "MLT"); catalogs.require("tax", "34");
        assertThrows(InvoiceValidationException.class, () -> catalogs.require("municipality", "99999"));
        assertThrows(InvoiceValidationException.class, () -> catalogs.require("vat-rate", "18"));
    }
    @Test void borradorConExtensionRealYSinFirmaFicticia() throws Exception {
        var draft = generate(base()); var doc = DianInvoiceDraftGenerator.parse(draft.xml());
        assertTrue(XSD.validate(draft.xml()).structurallyValid());
        assertFalse(draft.readyToTransmit()); assertTrue(draft.pending().contains("FIRMA_XADES"));
        assertEquals(1, doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.EXT, "UBLExtension").getLength());
        assertEquals(0, doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").getLength());
        assertEquals("18760000001", value(doc, DianInvoiceDraftGenerator.STS, "InvoiceAuthorization"));
        assertEquals("100", value(doc, DianInvoiceDraftGenerator.STS, "To"));
        assertEquals("UBL 2.1", value(doc, DianInvoiceDraftGenerator.CBC, "UBLVersionID"));
        assertEquals("2", value(doc, DianInvoiceDraftGenerator.CBC, "ProfileExecutionID"));
        assertEquals(draft.cufe(), value(doc, DianInvoiceDraftGenerator.CBC, "UUID"));
        assertTrue(value(doc, DianInvoiceDraftGenerator.STS, "QRCode").startsWith("https://catalogo-vpfe-hab.dian.gov.co/"));
        assertFalse(draft.xml().contains("CLAVE-FICTICIA"));
        assertTrue(draft.xml().contains("MEDELLÍN"));
        Files.createDirectories(Path.of("target/examples"));
        Files.writeString(Path.of("target/examples/dian19-unsigned.xml"), draft.xml());
    }
    @Test void ambienteYNumeracionCambianIdentificadoresSinTransmitir() {
        var invoice = base();
        try (var secrets = secrets()) {
            var generator = new DianInvoiceDraftGenerator();
            var test = generator.generate(invoice, profile(DianProfile.Environment.TEST), secrets);
            var production = generator.generate(invoice, profile(DianProfile.Environment.PRODUCTION), secrets);
            assertNotEquals(test.cufe(), production.cufe());
            assertTrue(production.xml().contains("https://catalogo-vpfe.dian.gov.co/"));
            assertEquals(test.xml(), generator.generate(invoice, profile(DianProfile.Environment.TEST), secrets).xml());
        }
    }
    @Test void xsdDianValidaExtensionYBloqueaAtaques() {
        var xml = generate(base()).xml();
        assertFalse(XSD.validate(xml.replace("<sts:InvoiceAuthorization>18760000001</sts:InvoiceAuthorization>",
                "<sts:InvoiceAuthorization>no-numerica</sts:InvoiceAuthorization>")).structurallyValid());
        assertFalse(XSD.validate(xml.replaceAll("<sts:SoftwareID[^>]*>.*?</sts:SoftwareID>", "")).structurallyValid());
        assertFalse(XSD.validate("<!DOCTYPE Invoice [<!ENTITY x SYSTEM 'file:///no-leer'>]><Invoice>&x;</Invoice>").structurallyValid());
    }
    @Test void correccionLocalNoOcultaLaInconsistenciaDelXsdPublicado() throws Exception {
        String xml = generate(base()).xml();
        var published = OfflineUblValidator.dian19Published().validate(xml);
        assertFalse(published.structurallyValid());
        assertTrue(published.issues().stream().allMatch(i -> i.description().contains("schemeID") || i.description().contains("enumeration")));
        assertTrue(XSD.validate(xml).structurallyValid());
        assertFalse(XSD.validate(xml.replace("schemeID=\"1\"", "schemeID=\"99\"")).structurallyValid());
        try (var input = getClass().getResourceAsStream("/dian19/official/exempt.xml")) {
            String official = new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(OfflineUblValidator.dian19Published().validate(official).issues().stream()
                    .anyMatch(i -> i.description().contains("schemeID")));
        }
    }
    @Test void gruposSeparadosPorTributoYBasesMixtasSinDuplicar() throws Exception {
        var iva = InvoiceFixtures.line("100", "19", FiscalInvoice.Treatment.TAXED);
        var inc = new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED, "04", "INC", n("8"));
        var dual = new FiscalInvoice.Line("DUAL", "Dos tributos explicitos", BigDecimal.ONE, "94", n("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(iva.taxes().getFirst(), inc));
        var mixed = invoice(List.of(dual, InvoiceFixtures.line("100", "5", FiscalInvoice.Treatment.TAXED),
                InvoiceFixtures.line("100", "0", FiscalInvoice.Treatment.EXCLUDED)), "332", List.of(), List.of());
        var totals = new InvoiceCalculator().calculate(mixed);
        assertEquals(n("200.00"), totals.taxableBase()); assertEquals(n("32.00"), totals.tax());
        var doc = DianInvoiceDraftGenerator.parse(generate(mixed).xml());
        long headerTaxTotals = 0;
        for (Node node = doc.getDocumentElement().getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element e && "TaxTotal".equals(e.getLocalName())) headerTaxTotals++;
        assertEquals(2, headerTaxTotals);
    }
    @Test void todosLosEjemplosOficialesSeConservanComoReferenciaDeEstructura() throws Exception {
        var ours = DianInvoiceDraftGenerator.parse(generate(base()).xml());
        for (String fixture : List.of("generic", "exempt", "excluded", "mixed", "ibua", "icui", "withholding")) {
            try (var input = getClass().getResourceAsStream("/dian19/official/" + fixture + ".xml")) {
                var doc = DianInvoiceDraftGenerator.parse(new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                for (String field : List.of("UBLVersionID", "InvoiceTypeCode", "DocumentCurrencyCode"))
                    assertEquals(value(doc, DianInvoiceDraftGenerator.CBC, field), value(ours, DianInvoiceDraftGenerator.CBC, field), fixture + ": " + field);
                for (String field : List.of("InvoiceControl", "InvoiceSource", "SoftwareProvider", "SoftwareSecurityCode", "AuthorizationProvider", "QRCode"))
                    assertEquals(1, doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.STS, field).getLength(), fixture + ": " + field);
            }
        }
    }
    @Test void redondeoAlParEmpatesPositivosYNegativos() {
        assertEquals(n("1.00"), Amounts.money(n("1.005")));
        assertEquals(n("1.02"), Amounts.money(n("1.015")));
        assertEquals(n("-1.00"), Amounts.money(n("-1.005")));
    }
    @Test void retencionesAgrupadasPorTributoNoDescuentanElTotal() throws Exception {
        var invoice = invoice(base().lines(), "119", List.of(), List.of(
                new FiscalInvoice.Withholding("06", "ReteRenta", n("100"), n("2.5")),
                new FiscalInvoice.Withholding("06", "ReteRenta", n("50"), n("4"))));
        var totals = new InvoiceCalculator().calculate(invoice);
        assertEquals(n("4.50"), totals.withholding()); assertEquals(n("119.00"), totals.payable());
        var doc = DianInvoiceDraftGenerator.parse(generate(invoice).xml());
        assertEquals(1, doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.CAC, "WithholdingTaxTotal").getLength());
    }
    @Test void rechazaAjustesExcesivosYTarifaIBUADeOtroAno() {
        assertThrows(InvoiceValidationException.class, () -> generate(invoice(base().lines(), "0",
                List.of(new FiscalInvoice.Adjustment(false,"00","Excesivo",n("120"))), List.of())));
        var tax = new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED,"34","IBUA",BigDecimal.ZERO,
                new FiscalInvoice.UnitTax(n("100"),"MLT",n("100"),n("38")));
        var line = new FiscalInvoice.Line("B","Tarifa 2025 en 2026",BigDecimal.ONE,"94",n("100"),BigDecimal.ZERO,BigDecimal.ZERO,List.of(tax));
        assertThrows(InvoiceValidationException.class, () -> generate(invoice(List.of(line),"138",List.of(),List.of())));
    }
    @Test void descuentosFinancierosGlobalesNoRedistribuyenBasesYRetencionNoReduceTotal() throws Exception {
        var invoice = invoice(base().lines(), "114", List.of(new FiscalInvoice.Adjustment(false, "00", "Descuento financiero", n("10")),
                new FiscalInvoice.Adjustment(true, "02", "Cargo financiero", n("5"))),
                List.of(new FiscalInvoice.Withholding("06", "ReteRenta", n("100"), n("2.5"))));
        var totals = new InvoiceCalculator().calculate(invoice);
        assertEquals(n("100.00"), totals.taxableBase()); assertEquals(n("19.00"), totals.tax());
        assertEquals(n("119.00"), totals.inclusive()); assertEquals(n("114.00"), totals.payable());
        assertEquals(n("2.50"), totals.withholding());
        var doc = DianInvoiceDraftGenerator.parse(generate(invoice).xml());
        assertEquals("114.00", value(doc, DianInvoiceDraftGenerator.CBC, "PayableAmount"));
        assertEquals(1, doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.CAC, "WithholdingTaxTotal").getLength());
    }
    @Test void tributosPorUnidadIBUA2026YNominal() throws Exception {
        var ibua = new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED, "34", "IBUA", BigDecimal.ZERO,
                new FiscalInvoice.UnitTax(n("350"), "MLT", n("100"), n("40")));
        var line = new FiscalInvoice.Line("BEBIDA", "Ejemplo ficticio", BigDecimal.ONE, "94", n("1000"), BigDecimal.ZERO, BigDecimal.ZERO, List.of(ibua));
        var invoice = invoice(List.of(line), "1140", List.of(), List.of());
        assertEquals(n("140.00"), new InvoiceCalculator().calculate(invoice).tax());
        var doc = DianInvoiceDraftGenerator.parse(generate(invoice).xml());
        assertEquals("350", value(doc, DianInvoiceDraftGenerator.CBC, "BaseUnitMeasure"));
        assertEquals("40.00", value(doc, DianInvoiceDraftGenerator.CBC, "PerUnitAmount"));
        var bags = new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED, "22", "INCBolsas", BigDecimal.ZERO,
                new FiscalInvoice.UnitTax(n("2"), "94", BigDecimal.ONE, n("10")));
        var bagLine = new FiscalInvoice.Line("BOLSA", "Tarifa ficticia, no legal", n("2"), "94", n("100"), BigDecimal.ZERO, BigDecimal.ZERO, List.of(bags));
        assertEquals(n("20.00"), new InvoiceCalculator().calculate(invoice(List.of(bagLine), "220", List.of(), List.of())).tax());
        generate(invoice(List.of(bagLine), "220", List.of(), List.of()));
    }
    @Test void referenciasOficialesExentoExcluidoCoincidenEnSemantica() throws Exception {
        for (var treatment : List.of(FiscalInvoice.Treatment.EXEMPT, FiscalInvoice.Treatment.EXCLUDED)) {
            String fixture = treatment == FiscalInvoice.Treatment.EXEMPT ? "exempt" : "excluded";
            try (var input = getClass().getResourceAsStream("/dian19/official/" + fixture + ".xml")) {
                var official = DianInvoiceDraftGenerator.parse(new String(Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                var ours = DianInvoiceDraftGenerator.parse(generate(invoice(List.of(InvoiceFixtures.line("50000", "0", treatment)), "50000", List.of(), List.of())).xml());
                for (String field : List.of("LineExtensionAmount", "TaxExclusiveAmount", "TaxInclusiveAmount", "PayableAmount")) {
                    assertEquals(value(official, DianInvoiceDraftGenerator.CBC, field), value(ours, DianInvoiceDraftGenerator.CBC, field), fixture + ": " + field);
                }
                assertEquals(official.getElementsByTagNameNS(DianInvoiceDraftGenerator.CAC, "TaxTotal").getLength(),
                        ours.getElementsByTagNameNS(DianInvoiceDraftGenerator.CAC, "TaxTotal").getLength());
            }
        }
    }
    @Test void catalogosDvYRelacionDepartamentoSeRechazanAntesDelXml() {
        var base = base(); var p = base.issuer();
        var wrongDv = new FiscalInvoice.Party("31", p.identification(), "9", p.name(), p.taxResponsibilities(), p.taxSchemeId(), p.taxSchemeName(), p.address());
        assertThrows(InvoiceValidationException.class, () -> generate(replaceIssuer(base, wrongDv)));
        var a = p.address();
        var wrongDept = new FiscalInvoice.Address(a.street(), "05001", a.cityName(), "11", "Bogota", "CO");
        var bad = new FiscalInvoice.Party("31", p.identification(), p.checkDigit(), p.name(), p.taxResponsibilities(), p.taxSchemeId(), p.taxSchemeName(), wrongDept);
        assertThrows(InvoiceValidationException.class, () -> generate(replaceIssuer(base, bad)));
        assertThrows(InvoiceValidationException.class, () -> generate(invoice(List.of(InvoiceFixtures.line("100", "18", FiscalInvoice.Treatment.TAXED)), "118", List.of(), List.of())));
    }
    private static FiscalInvoice replaceIssuer(FiscalInvoice base, FiscalInvoice.Party issuer) {
        return new FiscalInvoice(base.sourceSaleId(), base.number(), base.issuedAt(), base.currency(), issuer, base.customer(), base.numbering(), base.payment(), base.lines(), base.declaredPayable());
    }
}
