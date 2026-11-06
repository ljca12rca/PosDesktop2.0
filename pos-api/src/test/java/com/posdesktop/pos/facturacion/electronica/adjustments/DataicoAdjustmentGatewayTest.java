package com.posdesktop.pos.facturacion.electronica.adjustments;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalPreflightException;
import com.posdesktop.pos.invoicing.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DataicoAdjustmentGatewayTest {
    static final String ACCOUNT="11111111-1111-4111-8111-111111111111";
    static DataicoProperties properties(String account){return new DataicoProperties(true,DataicoProperties.DEFAULT_BASE_URL,account,null,"test-not-real-token","FE","18760000001","1",true,"PRUEBAS",Duration.ofSeconds(2),Duration.ofSeconds(10));}
    static AdjustmentSnapshot snapshot(AdjustmentKind kind){
        var line=new FiscalInvoice.Line("SKU","Producto & original",new BigDecimal("3"),"94",new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,List.of(new FiscalInvoice.Tax(FiscalInvoice.Treatment.TAXED,"01","IVA",new BigDecimal("19"))));
        var source=new AdjustmentSource(UUID.randomUUID(),UUID.randomUUID(),null,"DATAICO","PRUEBAS",ACCOUNT,DataicoProperties.DEFAULT_BASE_URL,"FE7","original-uuid","a".repeat(96),LocalDate.of(2026,10,30),"Cliente",new BigDecimal("357"),List.of(line),null,Map.of("customer",Map.of("party_identification","123"),"items",List.of(Map.of("sku","SKU","description","Producto & original","quantity",3,"price",100,"taxes",List.of(Map.of("tax_category","IVA","tax_rate",19))))),"b".repeat(64));
        var plan=new AdjustmentCalculator().calculate(source.lines(),List.of(),false,kind,kind==AdjustmentKind.CANCELLATION?List.of():List.of(new AdjustmentCalculator.Selection(1,BigDecimal.ONE,BigDecimal.TEN)));
        String prefix=kind.credit()?"NC":"ND";var at=OffsetDateTime.parse("2026-11-06T10:00:00-05:00");
        return new AdjustmentSnapshot(1,"requesthash",source,plan,prefix,42,at,"Motivo de prueba",DataicoAdjustmentGateway.request(source,plan,prefix,42,at,"Motivo de prueba"));
    }
    @Test void sendsBothKindsWithOfficialEnvelopesOriginalInvoiceAndConfiguredCredentials(){
        for(var kind:AdjustmentKind.values()){
            var p=properties(ACCOUNT);var builder=RestClient.builder().baseUrl(p.baseUrl());var server=MockRestServiceServer.bindTo(builder).build();var s=snapshot(kind);String key=kind.credit()?"credit_note":"debit_note",path=kind.credit()?"credit_notes":"debit_notes";
            server.expect(requestTo(p.baseUrl()+"/"+path)).andExpect(method(HttpMethod.POST)).andExpect(header("Auth-token","test-not-real-token")).andExpect(header("x-on-behalf-of",ACCOUNT)).andExpect(header("Dataico_account_id",ACCOUNT))
                .andExpect(content().json("{\""+key+"\":{\"dataico_account_id\":\""+ACCOUNT+"\",\"invoice_id\":\"original-uuid\",\"number\":\"42\",\"reason\":\""+kind.dataicoReason()+"\",\"numbering\":{\"prefix\":\""+s.prefix()+"\",\"flexible\":true}},\"actions\":{\"send_dian\":true,\"send_email\":false}}"))
                .andRespond(withSuccess("{\""+key+"\":{\"uuid\":\"created-uuid\",\"number\":\"42\",\"dian_status\":\"DIAN_PENDIENTE\"}}",MediaType.APPLICATION_JSON));
            var client=builder.build();var gateway=new DataicoAdjustmentGateway(()->p,unused->client);var result=gateway.send(s);assertEquals("created-uuid",result.id());assertEquals(s.number(),result.number());assertEquals("DIAN_PENDIENTE",result.dianStatus());server.verify();
        }
    }
    @Test void consultUsesGetAndOriginalNumberNeverAnotherPost(){
        var p=properties(ACCOUNT);var b=RestClient.builder().baseUrl(p.baseUrl());var server=MockRestServiceServer.bindTo(b).build();var s=snapshot(AdjustmentKind.RETURN);
        server.expect(requestTo(p.baseUrl()+"/credit_notes?number=NC42")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"data\":[{\"uuid\":\"existing\",\"number\":\"NC42\",\"dian_status\":\"DIAN_ACEPTADO\"}]}",MediaType.APPLICATION_JSON));
        var client=b.build();assertEquals("existing",new DataicoAdjustmentGateway(()->p,unused->client).consult(s).id());server.verify();
    }
    @Test void refusesAccountChangeAndUnverifiableSuccess(){
        var s=snapshot(AdjustmentKind.RETURN);assertThrows(FiscalPreflightException.class,()->new DataicoAdjustmentGateway(()->properties("different-account"),unused->{fail("No HTTP permitted");return null;}).send(s));
        var p=properties(ACCOUNT);var b=RestClient.builder().baseUrl(p.baseUrl());var server=MockRestServiceServer.bindTo(b).build();server.expect(requestTo(p.baseUrl()+"/credit_notes")).andRespond(withSuccess("{\"uuid\":\"unrelated\",\"number\":\"999\"}",MediaType.APPLICATION_JSON));var client=b.build();assertThrows(IllegalStateException.class,()->new DataicoAdjustmentGateway(()->p,unused->client).send(s));server.verify();
    }
}
