package com.posdesktop.pos.facturacion.electronica.infraestructura.dataico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DataicoEstadoCorreoTest {
    @Test
    void tomaElTokenVigenteEnCadaLlamadaSinReconstruirElProveedor() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dataico.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var currentToken = new java.util.concurrent.atomic.AtomicReference<>("primer_token_ficticio");
        server.expect(requestTo("https://dataico.example.test/invoices?number=FEE1"))
                .andExpect(header("Auth-token", "primer_token_ficticio"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dataico.example.test/invoices/uuid-existente"))
                .andExpect(header("Auth-token", "segundo_token_ficticio"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        DataicoProperties properties = new DataicoProperties(true, null, null, "yaml-no-usado", null,
                "FEE", "18760000001", "1", true, "PRUEBAS", Duration.ofSeconds(5), Duration.ofSeconds(30));
        var provider = new DataicoFacturacionElectronicaProvider(builder.build(), properties,
                new DataicoInvoiceMapper(properties), currentToken::get);

        provider.consultarFactura("FEE1");
        currentToken.set("segundo_token_ficticio");
        provider.reenviar("uuid-existente", false, true, "nuevo@example.test");

        server.verify();
    }

    @Test
    void enviaCorreoConElDestinoEditadoSinSolicitarEnvioDian() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dataico.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dataico.example.test/invoices/uuid-existente"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"actions":{"send_dian":false,"send_email":true,"email":"nuevo@example.test"}}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        DataicoProperties properties = new DataicoProperties(true, null, null, "token-prueba", null,
                "FEE", "18760000001", "1", true, "PRUEBAS", Duration.ofSeconds(5), Duration.ofSeconds(30));
        DataicoFacturacionElectronicaProvider provider = new DataicoFacturacionElectronicaProvider(
                builder.build(), properties, new DataicoInvoiceMapper(properties));

        provider.reenviar("uuid-existente", false, true, "nuevo@example.test");

        server.verify();
    }

    @Test
    void obtieneElEstadoRealDelCorreoDesdeLaFacturaDeDataico() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dataico.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dataico.example.test/invoices?number=FEE1"))
                .andRespond(withSuccess("""
                        {"invoice":{"uuid":"id-dataico","number":"FEE1",
                         "dian_status":"DIAN_RECHAZADO","email_status":"CORREO_NO_ENVIADO"}}
                        """, MediaType.APPLICATION_JSON));
        DataicoProperties properties = new DataicoProperties(true, null, null, "token-prueba", null,
                "FEE", "18760000001", "1", true, "PRUEBAS", Duration.ofSeconds(5), Duration.ofSeconds(30));
        DataicoFacturacionElectronicaProvider provider = new DataicoFacturacionElectronicaProvider(
                builder.build(), properties, new DataicoInvoiceMapper(properties));

        var resultado = provider.consultarFactura("FEE1");

        assertThat(resultado.identificadorExterno()).isEqualTo("id-dataico");
        assertThat(resultado.estado()).isEqualTo("DIAN_RECHAZADO");
        assertThat(resultado.estadoCorreo()).isEqualTo("CORREO_NO_ENVIADO");
        server.verify();
    }
}
