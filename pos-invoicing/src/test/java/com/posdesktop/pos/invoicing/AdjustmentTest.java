package com.posdesktop.pos.invoicing;
import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.domain.dian.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdjustmentTest {
    static BigDecimal n(String n){return new BigDecimal(n);}
    static FiscalInvoice.Line line(){return new FiscalInvoice.Line("sku","Artículo & ñ",n("3"),"94",n("100"),n("10"),BigDecimal.ZERO,List.of(new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED,"01","IVA",n("19"))));}
    @Test void partialReturnRetainsTaxAndDistributesDiscountRemainder(){
        var calc=new AdjustmentCalculator();var source=List.of(line());
        var first=calc.calculate(source,List.of(),false,AdjustmentKind.RETURN,List.of(new AdjustmentCalculator.Selection(1,n("1"),BigDecimal.ZERO)));
        assertEquals(n("3.33"),first.allocations().getFirst().line().discount());assertEquals(n("115.04"),first.total());
        var last=calc.calculate(source,first.allocations(),true,AdjustmentKind.RETURN,List.of(new AdjustmentCalculator.Selection(1,n("2"),BigDecimal.ZERO)));
        assertEquals(n("6.67"),last.allocations().getFirst().line().discount());assertEquals(n("230.06"),last.total());assertEquals(n("345.10"),first.total().add(last.total()));
    }
    @Test void blocksOverReturnDuplicatesZeroAndCancellationAfterAnotherAdjustment(){
        var calc=new AdjustmentCalculator();var s=List.of(line());var q=new AdjustmentCalculator.Selection(1,n("4"),BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,()->calc.calculate(s,List.of(),false,AdjustmentKind.RETURN,List.of(q)));
        assertThrows(IllegalArgumentException.class,()->calc.calculate(s,List.of(),false,AdjustmentKind.RETURN,List.of(q,q)));
        assertThrows(IllegalArgumentException.class,()->calc.calculate(s,List.of(),false,AdjustmentKind.RETURN,List.of()));
        assertThrows(IllegalArgumentException.class,()->calc.calculate(s,List.of(),true,AdjustmentKind.CANCELLATION,List.of()));
    }
    @Test void cancellationCopiesWholeInvoiceAndIncreaseOnlyChargesDifference(){
        var calc=new AdjustmentCalculator();var source=List.of(line());
        assertEquals(n("345.10"),calc.calculate(source,List.of(),false,AdjustmentKind.CANCELLATION,List.of()).total());
        var increase=calc.calculate(source,List.of(),false,AdjustmentKind.INCREASE,List.of(new AdjustmentCalculator.Selection(1,n("2"),n("10"))));
        assertEquals(n("23.80"),increase.total());assertEquals(n("0.00"),increase.allocations().getFirst().line().discount());
    }
    @Test void officialCreditCudeVectorPage666(){try(var s=new DianSecrets("not-used".toCharArray(),"12301".toCharArray())){
        assertEquals("907e4444decc9e59c160a2fb3b6659b33dc5b632a5008922b9a62f83f757b1c448e47f5867f2b50dbdb96f48c7681168",DianIdentifiers.cude(new DianIdentifiers.CufeInput("8110007871","2019-01-12","07:00:00-05:00",n("5000"),n("950"),n("0"),n("0"),n("5950"),"900373076","8355990","1"),s));}}
    // El hash impreso en p669 no corresponde a su concatenacion. Formula verificada tambien con hashlib.
    @Test void debitCudeUsesPublishedConcatenationNotInconsistentPrintedHash(){try(var s=new DianSecrets("not-used".toCharArray(),"10201".toCharArray())){
        assertEquals("3fa73a86d57d9341c536afde1f85c4efd9d4591c2c22bce4dfb0e6b0d2e83b8f047a8bde7098292e9d2493e60d1c31da",DianIdentifiers.cude(new DianIdentifiers.CufeInput("ND1001","2019-01-18","10:58:00-05:00",n("30000"),n("0"),n("2400"),n("0"),n("32400"),"900197264","10254102","2"),s));}}
    static FiscalInvoice note(AdjustmentKind kind){
        var p=InvoiceFixtures.party();var party=new FiscalInvoice.Party("31","860324218","1",p.name(),p.taxResponsibilities(),"01","IVA",p.address());
        var date=LocalDate.of(2026,11,6);String prefix=kind.credit()?"NC":"ND";
        return new FiscalInvoice(UUID.randomUUID(),prefix+"1",date.atTime(10,30).atOffset(ZoneOffset.ofHours(-5)),Currency.getInstance("COP"),party,party,new FiscalInvoice.Numbering(prefix,"1",date,date,1,100),new FiscalInvoice.Payment("10",date,"ORIGINAL1"),List.of(line()),n("345.10"));
    }
    static String xml(AdjustmentKind kind){try(var secrets=new DianSecrets("not-used".toCharArray(),"12345".toCharArray())){
        return new DianAdjustmentGenerator().generate(note(kind),new DianProfile(DianProfile.Environment.TEST,UUID.randomUUID(),"860324218","1","1","1","1"),secrets,kind,new DianAdjustmentGenerator.Reference("FE1","a".repeat(96),LocalDate.of(2026,10,30)),"Motivo de prueba & revisión").xml();}}
    @Test void generatesBothSchemasWithOriginalReferenceAndCude()throws Exception {
        for(var kind:AdjustmentKind.values()){
            String xml=xml(kind);assertTrue(OfflineUblValidator.dian19Note(kind.credit()).validate(xml).structurallyValid());
            var doc=DianInvoiceDraftGenerator.parse(xml);assertEquals(kind.root(),doc.getDocumentElement().getLocalName());
            assertEquals(0,doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.STS,"InvoiceControl").getLength());
            assertEquals(kind.reason(),doc.getElementsByTagNameNS(DianInvoiceDraftGenerator.CBC,"ResponseCode").item(0).getTextContent());
            assertTrue(xml.contains("CUDE-SHA384"));assertTrue(xml.contains("FE1"));assertTrue(xml.contains("a".repeat(96)));assertFalse(xml.contains("not-used"));
        }
    }
    @Test void signsBothNoteTypesAndDetectsTampering(@TempDir Path dir)throws Exception {
        Path key=dir.resolve("test.p12");String exe=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        var pb=new ProcessBuilder(exe,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-dname","CN=LOCAL TEST ONLY, C=CO","-startdate","-1d","-validity","90","-keystore",key.toString(),"-storetype","PKCS12","-storepass:env","TEST_NOTE_PASS","-noprompt").redirectErrorStream(true);pb.environment().put("TEST_NOTE_PASS","test-only-password");var proc=pb.start();String output=new String(proc.getInputStream().readAllBytes());assertEquals(0,proc.waitFor(),output);
        var now=Instant.now();var material=Pkcs12SigningKey.load(Files.readAllBytes(key),"test-only-password".toCharArray(),now);var signer=new DianXadesSignature();
        for(var kind:List.of(AdjustmentKind.RETURN,AdjustmentKind.INCREASE)){
            var signed=signer.sign(xml(kind),material,now);assertTrue(signer.verify(signed,now).valid());String changed=signed.replace("Motivo","Alterado");assertNotEquals(signed,changed);assertFalse(signer.verify(changed,now).valid());
            assertTrue(OfflineUblValidator.dian19Note(kind.credit()).validate(signed).structurallyValid());
            Path file=Path.of("target","signature-review",kind.root()+".xml");Files.createDirectories(file.getParent());Files.writeString(file,signed);
        }
    }
}
