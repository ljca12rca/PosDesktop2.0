package com.posdesktop.pos.invoicing;
import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.infrastructure.transport.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class DianTransportTest {
    private final DianSoapResponseParser parser=new DianSoapResponseParser();
    private static final String KEY="a".repeat(96),FILE="fv0800197268000260000000b.xml";
    static String soap(String inner){return "<s:Envelope xmlns:s=\""+DianSoapResponseParser.SOAP+"\"><s:Body>"+inner+"</s:Body></s:Envelope>";}
    static String status(String code,boolean valid,String key,String file,String errors){return soap("<GetStatusZipResponse xmlns=\""+DianSoapResponseParser.WCF+"\"><GetStatusZipResult xmlns:r=\""+DianSoapResponseParser.RESPONSE+"\"><r:DianResponse><r:IsValid>"+valid+"</r:IsValid><r:StatusCode>"+code+"</r:StatusCode><r:XmlDocumentKey>"+key+"</r:XmlDocumentKey><r:XmlFileName>"+file+"</r:XmlFileName>"+errors+"</r:DianResponse></GetStatusZipResult></GetStatusZipResponse>");}
    @Test void archivePreservesExactUtf8XmlAndUsesIndependentHexFileCounter()throws Exception {
        for(String xml:List.of(XadesSignatureTest.draft(),AdjustmentTest.xml(com.posdesktop.pos.invoicing.domain.AdjustmentKind.RETURN),AdjustmentTest.xml(com.posdesktop.pos.invoicing.domain.AdjustmentKind.INCREASE))){
            var identity=DianDocumentIdentity.read(xml);var archive=new DianZipPackage().create(xml,11,2026);
            assertTrue(archive.xmlName().startsWith(identity.filePrefix()));assertTrue(archive.xmlName().endsWith("260000000b.xml"));assertTrue(archive.zipName().startsWith("z"));
            try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(archive.bytes()),StandardCharsets.UTF_8)){assertEquals(archive.xmlName(),zip.getNextEntry().getName());assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8),zip.readAllBytes());assertNull(zip.getNextEntry());}
            byte[] altered=archive.bytes();altered[0]=0;assertNotEquals(0,archive.bytes()[0]);
            assertArrayEquals(archive.bytes(),new DianZipPackage().create(xml,11,2026).bytes());
        }
        assertThrows(IllegalArgumentException.class,()->new DianZipPackage().create(XadesSignatureTest.draft(),0,2026));
        assertThrows(IllegalArgumentException.class,()->new DianZipPackage().create(XadesSignatureTest.draft(),0x100000000L,2026));
    }
    @Test void zipReceiptNeverMeansDocumentAcceptance(){var r=parser.upload(soap("<SendTestSetAsyncResponse xmlns=\""+DianSoapResponseParser.WCF+"\"><SendTestSetAsyncResult xmlns:u=\""+DianSoapResponseParser.UPLOAD+"\"><u:ZipKey>abc-123</u:ZipKey></SendTestSetAsyncResult></SendTestSetAsyncResponse>"));assertEquals(DianSoapResponseParser.State.RECEIVED,r.state());assertEquals("abc-123",r.trackId());}
    @Test void acceptsOnlyPositiveCorrelatedResultWithoutErrors(){assertEquals(DianSoapResponseParser.State.ACCEPTED,parser.status(status("00",true,KEY,FILE,""),"GetStatusZip",KEY,FILE).state());assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.status(status("00",true,"b".repeat(96),FILE,""),"GetStatusZip",KEY,FILE).state());assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.status(status("00",true,KEY,"other.xml",""),"GetStatusZip",KEY,FILE).state());assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.status(status("00",true,KEY,FILE,"<r:ErrorMessage><a:string xmlns:a=\"http://schemas.microsoft.com/2003/10/Serialization/Arrays\">Regla incumplida</a:string></r:ErrorMessage>"),"GetStatusZip",KEY,FILE).state());}
    @Test void distinguishesPendingRejectedAndTechnicalFault(){assertEquals(DianSoapResponseParser.State.PENDING,parser.status(status("98",false,"",FILE,""),"GetStatusZip",KEY,FILE).state());assertEquals(DianSoapResponseParser.State.REJECTED,parser.status(status("99",false,KEY,FILE,""),"GetStatusZip",KEY,FILE).state());assertEquals(DianSoapResponseParser.State.FAULT,parser.upload(soap("<s:Fault><s:Reason><s:Text>Error</s:Text></s:Reason></s:Fault>")).state());assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.upload("not XML").state());}
    @Test void blocksExternalEntitiesAndAmbiguousMatches(){String xml=status("00",true,KEY,FILE,"");int start=xml.indexOf("<r:DianResponse>"),end=xml.indexOf("</r:DianResponse>")+"</r:DianResponse>".length();String duplicate=xml.substring(start,end);assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.status(xml.replace("</GetStatusZipResult>",duplicate+"</GetStatusZipResult>"),"GetStatusZip",KEY,FILE).state());assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.upload("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///invalid'>]>"+xml).state());}
    @Test void decodesApplicationResponseAndSupportsDocumentKeyFallback(){
        String application="<ApplicationResponse xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\"/>";
        String payload="<r:XmlBase64Bytes>"+Base64.getEncoder().encodeToString(application.getBytes(StandardCharsets.UTF_8))+"</r:XmlBase64Bytes>";
        String xml=status("99",false,KEY,FILE,payload).replace("GetStatusZip","GetStatus").replace("<r:DianResponse>","").replace("</r:DianResponse>","");
        var reply=parser.status(xml,"GetStatus",KEY,FILE);assertEquals(DianSoapResponseParser.State.REJECTED,reply.state());assertEquals(application,reply.applicationResponse());
        String invalid=status("00",true,KEY,FILE,"<r:XmlBase64Bytes>not-base64!!</r:XmlBase64Bytes>");assertEquals(DianSoapResponseParser.State.UNKNOWN,parser.status(invalid,"GetStatusZip",KEY,FILE).state());
    }
}
