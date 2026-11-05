package com.posdesktop.pos.invoicing;

import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class XadesSignatureTest {
    @TempDir static Path temp;
    static byte[] p12,expired,other;
    static final char[] PASSWORD="only-test-password".toCharArray();
    static final Instant NOW=Instant.now();
    final DianXadesSignature signer=new DianXadesSignature();
    @BeforeAll static void certificates()throws Exception {
        p12=certificate("local",null);other=certificate("other",null);expired=certificate("expired","2020/01/01 00:00:00");
    }
    private static byte[] certificate(String name,String start)throws Exception {
        Path file=temp.resolve(name+".p12");
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        var args=new ArrayList<>(List.of(executable,"-genkeypair","-alias","local","-keyalg","RSA","-keysize","2048","-sigalg","SHA256withRSA","-dname","CN=SOLO PRUEBAS LOCALES "+name+", O=POSDesktop TEST, C=CO","-validity",start==null?"365":"1","-storetype","PKCS12","-keystore",file.toString(),"-storepass:env","POS_TEST_PASS","-keypass:env","POS_TEST_PASS","-ext","KU=digitalSignature,nonRepudiation","-noprompt"));
        args.add("-startdate");args.add(start==null?"-1d":start);
        var builder=new ProcessBuilder(args).redirectErrorStream(true);builder.environment().put("POS_TEST_PASS",new String(PASSWORD));
        var process=builder.start();String output=new String(process.getInputStream().readAllBytes());assertEquals(0,process.waitFor(),output);
        return Files.readAllBytes(file);
    }
    static String draft() {
        var original=InvoiceFixtures.invoice(List.of(InvoiceFixtures.line("100","19",FiscalInvoice.Treatment.TAXED)),"119");
        var p=original.issuer();var issuer=new FiscalInvoice.Party("31","860324218","1",p.name(),p.taxResponsibilities(),p.taxSchemeId(),p.taxSchemeName(),p.address());
        var invoice=new FiscalInvoice(original.sourceSaleId(),original.number(),original.issuedAt(),original.currency(),issuer,issuer,
                new FiscalInvoice.Numbering("PRUEBA","18760000001",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),1,100),original.payment(),original.lines(),original.declaredPayable());
        try(var secrets=new DianSecrets("TECH-TEST".toCharArray(),"PIN-TEST".toCharArray())) {
            return new DianInvoiceDraftGenerator().generate(invoice,new DianProfile(DianProfile.Environment.TEST,UUID.randomUUID(),"860324218","1","1","1","1"),secrets).xml();
        }
    }
    String signed(){return signer.sign(draft(),Pkcs12SigningKey.load(p12,PASSWORD,NOW),NOW);}
    @Test void signsReparsedDianXmlAndValidatesAllThreeReferences()throws Exception {
        String xml=signed();var validation=signer.verify(xml,NOW);
        assertTrue(validation.valid());assertFalse(validation.trustVerified());assertFalse(validation.readyToTransmit());
        assertEquals(64,validation.certificateSha256().length());
        assertTrue(OfflineUblValidator.dian19().validate(xml).structurallyValid());
        var doc=DianInvoiceDraftGenerator.parse(xml);
        assertEquals(3,doc.getElementsByTagNameNS(DianXadesSignature.DS,"Reference").getLength());
        assertEquals(2,doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.EXT,"UBLExtension").getLength());
        assertFalse(xml.contains("PRIVATE KEY"));assertFalse(xml.contains(new String(PASSWORD)));
        Path output=Path.of("target","signature-review","signed-test-invoice.xml");Files.createDirectories(output.getParent());Files.writeString(output,xml);
    }
    @Test void detectsChangesToAmountDescriptionAndSigningProperties(){
        String xml=signed();assertFalse(signer.verify(xml.replace("119.00","120.00"),NOW).valid());
        assertFalse(signer.verify(xml.replace("Articulo","Manipulado"),NOW).valid());
        assertFalse(signer.verify(xml.replace("supplier","third party"),NOW).valid());
        assertFalse(signer.verify(xml.replace(DianXadesSignature.POLICY,"https://invalid.test/policy"),NOW).valid());
    }
    @Test void rejectsCertificateReplacement()throws Exception {
        String xml=signed();var doc=DianInvoiceDraftGenerator.parse(xml);
        var cert=Pkcs12SigningKey.load(other,PASSWORD,NOW).chain().getFirst();
        doc.getElementsByTagNameNS(DianXadesSignature.DS,"X509Certificate").item(0).setTextContent(Base64.getEncoder().encodeToString(cert.getEncoded()));
        var out=new java.io.StringWriter();javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(new javax.xml.transform.dom.DOMSource(doc),new javax.xml.transform.stream.StreamResult(out));
        assertNotEquals(xml,out.toString());assertFalse(signer.verify(out.toString(),NOW).valid());
    }
    @Test void rejectsWrongPasswordExpiredAndGarbage(){
        assertThrows(IllegalArgumentException.class,()->Pkcs12SigningKey.load(p12,"wrong".toCharArray(),NOW));
        assertThrows(IllegalArgumentException.class,()->Pkcs12SigningKey.load(expired,PASSWORD,NOW));
        assertThrows(IllegalArgumentException.class,()->Pkcs12SigningKey.load(new byte[3],PASSWORD,NOW));
    }
    @Test void refusesDoubleSigning(){assertThrows(IllegalArgumentException.class,()->signer.sign(signed(),Pkcs12SigningKey.load(p12,PASSWORD,NOW),NOW));}
    @Test void rejectsUnsignedXxeAndExternalReference(){
        assertFalse(signer.verify(draft(),NOW).valid());
        assertFalse(signer.verify("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///no-such-file'>]><x>&e;</x>",NOW).valid());
        assertFalse(signer.verify(signed().replace("URI=\"\"","URI=\"https://example.com/payload\""),NOW).valid());
    }
    @Test void rejectsWrappingDuplicateIdsAndRemovedSignature(){
        String xml=signed();assertFalse(signer.verify(xml.replace("<cbc:ID>","<cbc:ID Id=\"duplicate\">"),NOW).valid());
        assertFalse(signer.verify(xml.replace("<cbc:Note>","<cbc:Note Id=\"duplicate\">"),NOW).valid());
        assertFalse(signer.verify(xml.replaceFirst("<ds:SignatureValue[^>]*>[^<]+</ds:SignatureValue>","<ds:SignatureValue>AA==</ds:SignatureValue>"),NOW).valid());
    }
    @Test void expirationAfterSigningIsNotTrusted(){assertFalse(signer.verify(signed(),NOW.plus(Duration.ofDays(400))).valid());}
}
