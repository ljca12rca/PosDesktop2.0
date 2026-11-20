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
    static String response(String source,String code)throws Exception{
        var identity=com.posdesktop.pos.invoicing.infrastructure.transport.DianDocumentIdentity.read(source);
        String raw="<ApplicationResponse xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\" xmlns:cbc=\""+DianInvoiceDraftGenerator.CBC+"\" xmlns:cac=\""+DianInvoiceDraftGenerator.CAC+"\" xmlns:ext=\""+DianInvoiceDraftGenerator.EXT+"\"><ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions><cbc:ID>AR-TEST</cbc:ID><cbc:IssueDate>"+java.time.LocalDate.now(java.time.ZoneOffset.ofHours(-5))+"</cbc:IssueDate><cbc:IssueTime>10:00:00-05:00</cbc:IssueTime><cac:DocumentResponse><cac:Response><cbc:ResponseCode>"+code+"</cbc:ResponseCode></cac:Response><cac:DocumentReference><cbc:ID>"+identity.number()+"</cbc:ID><cbc:UUID>"+identity.key()+"</cbc:UUID></cac:DocumentReference></cac:DocumentResponse></ApplicationResponse>";
        var d=DianInvoiceDraftGenerator.parse(raw);var ks=java.security.KeyStore.getInstance("PKCS12");ks.load(new java.io.ByteArrayInputStream(p12),PASSWORD);var certificate=ks.getCertificate("local");var factory=javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM");var digest=factory.newDigestMethod(javax.xml.crypto.dsig.DigestMethod.SHA256,null);var reference=factory.newReference("",digest,List.of(factory.newTransform(javax.xml.crypto.dsig.Transform.ENVELOPED,(javax.xml.crypto.dsig.spec.TransformParameterSpec)null),factory.newTransform(javax.xml.crypto.dsig.CanonicalizationMethod.EXCLUSIVE,(javax.xml.crypto.dsig.spec.TransformParameterSpec)null)),null,null);var info=factory.newSignedInfo(factory.newCanonicalizationMethod(javax.xml.crypto.dsig.CanonicalizationMethod.EXCLUSIVE,(javax.xml.crypto.dsig.spec.C14NMethodParameterSpec)null),factory.newSignatureMethod(javax.xml.crypto.dsig.SignatureMethod.RSA_SHA256,null),List.of(reference));var keyInfo=factory.getKeyInfoFactory().newKeyInfo(List.of(factory.getKeyInfoFactory().newX509Data(List.of(certificate))));var context=new javax.xml.crypto.dsig.dom.DOMSignContext(ks.getKey("local",PASSWORD),d.getElementsByTagNameNS(DianInvoiceDraftGenerator.EXT,"ExtensionContent").item(0));context.setDefaultNamespacePrefix("ds");factory.newXMLSignature(info,keyInfo).sign(context);var output=new java.io.StringWriter();javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(new javax.xml.transform.dom.DOMSource(d),new javax.xml.transform.stream.StreamResult(output));return output.toString();
    }
    @Test void verifiesApplicationResponseWithoutTrustingItsEmbeddedCertificate()throws Exception{
        String source=draft(),ar=response(source,"02");var identity=com.posdesktop.pos.invoicing.infrastructure.transport.DianDocumentIdentity.read(source);var verifier=new ApplicationResponseVerifier();
        var unknown=verifier.verify(ar,identity.number(),identity.key(),Set.of(),NOW);assertTrue(unknown.signatureValid());assertTrue(unknown.correlated());assertFalse(unknown.trusted());
        assertTrue(verifier.verify(ar,identity.number(),identity.key(),Set.of(unknown.certificateSha256()),NOW).trusted());
        assertFalse(verifier.verify(ar,identity.number(),"b".repeat(96),Set.of(unknown.certificateSha256()),NOW).correlated());
        assertFalse(verifier.verify(ar.replace("AR-TEST","AR-TAMPERED"),identity.number(),identity.key(),Set.of(unknown.certificateSha256()),NOW).signatureValid());
        assertFalse(verifier.verify(ar,identity.number(),identity.key(),Set.of(unknown.certificateSha256()),NOW.plusSeconds(400L*86400)).signatureValid());
        assertFalse(verifier.verify("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///invalid'>]>"+ar,identity.number(),identity.key(),Set.of(),NOW).signatureValid());
    }
    @Test void containerPreservesOriginalXmlAndCorrelatedResponseAndSignsLocally()throws Exception{
        String source=signed(),ar=response(source,"02");var generator=new DianAttachedDocumentGenerator();var material=Pkcs12SigningKey.load(p12,PASSWORD,NOW);
        String container=signer.sign(generator.generate(source,ar,UUID.randomUUID(),NOW),material,NOW);assertTrue(signer.verify(container,NOW).valid());var result=OfflineUblValidator.dian19Container().validate(container);assertTrue(result.structurallyValid(),result.issues().toString());
        var d=DianInvoiceDraftGenerator.parse(container);var descriptions=d.getElementsByTagNameNS(DianInvoiceDraftGenerator.CBC,"Description");assertTrue(java.util.stream.IntStream.range(0,descriptions.getLength()).anyMatch(i->source.equals(descriptions.item(i).getTextContent())));assertTrue(java.util.stream.IntStream.range(0,descriptions.getLength()).anyMatch(i->ar.equals(descriptions.item(i).getTextContent())));
        assertThrows(IllegalArgumentException.class,()->generator.generate(source,response(source,"04"),UUID.randomUUID(),NOW));
        Path output=Path.of("target/signature-review");Files.createDirectories(output);Files.writeString(output.resolve("ApplicationResponse.xml"),ar);Files.writeString(output.resolve("AttachedDocument.xml"),container);
    }
    @Test void containersSupportCreditAndDebitNotesWithTheirCude()throws Exception{for(var kind:List.of(AdjustmentKind.RETURN,AdjustmentKind.INCREASE)){String source=signer.sign(AdjustmentTest.xml(kind),Pkcs12SigningKey.load(p12,PASSWORD,NOW),NOW);String container=signer.sign(new DianAttachedDocumentGenerator().generate(source,response(source,"02"),UUID.randomUUID(),NOW),Pkcs12SigningKey.load(p12,PASSWORD,NOW),NOW);assertTrue(signer.verify(container,NOW).valid());assertTrue(container.contains("CUDE-SHA384"));assertTrue(OfflineUblValidator.dian19Container().validate(container).structurallyValid());}}
    @Test void soapSignsDestinationTimestampBodyAndActionAndDetectsTampering()throws Exception {
        var envelope=new DianSoapEnvelope();var endpoint=java.net.URI.create("https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc");
        var archive=new com.posdesktop.pos.invoicing.infrastructure.transport.DianZipPackage().create(signed(),11,2026);
        var key=Pkcs12SigningKey.load(p12,PASSWORD,NOW);UUID testSet=UUID.randomUUID();
        var request=envelope.send(endpoint,archive,testSet,key,NOW);
        assertTrue(envelope.verify(request.xml(),endpoint,NOW));
        assertFalse(envelope.verify(request.xml().replace(testSet.toString(),UUID.randomUUID().toString()),endpoint,NOW));
        assertFalse(envelope.verify(request.xml(),java.net.URI.create("https://example.invalid"),NOW));
        assertFalse(envelope.verify(request.xml(),endpoint,NOW.plusSeconds(301)));
        assertFalse(envelope.verify(request.xml().replace("SendTestSetAsync","GetStatus"),endpoint,NOW));
        assertTrue(envelope.verify(envelope.status(endpoint,"SIM-123",true,key,NOW).xml(),endpoint,NOW));
        assertTrue(envelope.verify(envelope.status(endpoint,"a".repeat(96),false,key,NOW).xml(),endpoint,NOW));
        assertFalse(request.xml().contains(new String(PASSWORD)));assertFalse(request.xml().contains("PRIVATE KEY"));
        Path review=Path.of("target/signature-review");Files.createDirectories(review);Files.writeString(review.resolve("SoapEnvelope.xml"),request.xml());Files.write(review.resolve(archive.zipName()),archive.bytes());
    }
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
