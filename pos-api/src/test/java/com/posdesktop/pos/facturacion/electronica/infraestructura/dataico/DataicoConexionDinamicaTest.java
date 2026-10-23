package com.posdesktop.pos.facturacion.electronica.infraestructura.dataico;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import com.posdesktop.pos.facturacion.electronica.domain.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DataicoConexionDinamicaTest {
    private final String cuentaA = "11111111-1111-4111-8111-111111111111";
    private final String cuentaB = "22222222-2222-4222-8222-222222222222";

    @Test
    void cambiaCuentaTokenUrlYTimeoutsSinRecrearElProveedorYUsaLaMismaCuentaEnJsonYHeaders() {
        var configA = configuracion(true, cuentaA, DataicoProperties.DEFAULT_BASE_URL, "token_a_ficticio", 5, 30);
        var configB = configuracion(true, cuentaB, "https://api.dataico.com/direct/dataico_api", "token_b_ficticio", 7, 25);
        AtomicReference<DataicoProperties> vigente = new AtomicReference<>(configA);
        var builderA = RestClient.builder().baseUrl(configA.baseUrl());
        var builderB = RestClient.builder().baseUrl(configB.baseUrl());
        var serverA = MockRestServiceServer.bindTo(builderA).build();
        var serverB = MockRestServiceServer.bindTo(builderB).build();
        serverA.expect(requestTo(configA.baseUrl() + "/invoices?number=FEE1"))
                .andExpect(header("Auth-token", "token_a_ficticio"))
                .andExpect(header("Dataico_account_id", cuentaA))
                .andExpect(header("x-on-behalf-of", cuentaA))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        serverB.expect(requestTo(configB.baseUrl() + "/invoices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Auth-token", "token_b_ficticio"))
                .andExpect(header("Dataico_account_id", cuentaB))
                .andExpect(header("x-on-behalf-of", cuentaB))
                .andExpect(content().json("{\"invoice\":{\"dataico_account_id\":\"" + cuentaB + "\",\"env\":\"PRUEBAS\"}}"))
                .andRespond(withSuccess("{\"invoice\":{\"uuid\":\"prueba\",\"number\":\"2\"}}", MediaType.APPLICATION_JSON));
        RestClient clientA = builderA.build();
        RestClient clientB = builderB.build();
        AtomicReference<DataicoProperties> transport = new AtomicReference<>();
        var provider = new DataicoFacturacionElectronicaProvider(new DataicoInvoiceMapper(configA), vigente::get, current -> {
            transport.set(current);
            return current.accountId().equals(cuentaA) ? clientA : clientB;
        });

        provider.consultarFactura("FEE1");
        vigente.set(configB);
        provider.emitir(documento());

        serverA.verify();
        serverB.verify();
        assertThat(transport.get().connectTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(transport.get().readTimeout()).isEqualTo(Duration.ofSeconds(25));
        assertThat(transport.get().toString()).doesNotContain("token_b_ficticio");
    }

    @Test
    void deshabilitarDataicoEvitaInclusoConstruirLaConexionHttpYSePuedeVolverAHabilitar() {
        var disabled = configuracion(false, cuentaA, DataicoProperties.DEFAULT_BASE_URL, "token_ficticio", 5, 30);
        AtomicReference<DataicoProperties> vigente = new AtomicReference<>(disabled);
        var builder = RestClient.builder().baseUrl(DataicoProperties.DEFAULT_BASE_URL);
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(DataicoProperties.DEFAULT_BASE_URL + "/invoices?number=FEE1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        AtomicInteger clientes = new AtomicInteger();
        var provider = new DataicoFacturacionElectronicaProvider(new DataicoInvoiceMapper(disabled), vigente::get,
                current -> { clientes.incrementAndGet(); return builder.build(); });
        assertThatThrownBy(() -> provider.consultarFactura("FEE1")).hasMessageContaining("deshabilitado");
        assertThat(clientes.get()).isZero();
        vigente.set(configuracion(true, cuentaA, DataicoProperties.DEFAULT_BASE_URL, "token_ficticio", 5, 30));
        provider.consultarFactura("FEE1");
        assertThat(clientes.get()).isEqualTo(1);
        server.verify();
    }

    private DataicoProperties configuracion(boolean enabled, String account, String url, String token, int connect, int read) {
        return new DataicoProperties(enabled, url, account, null, token, "FEE", "18760000001", "1", true,
                "PRUEBAS", Duration.ofSeconds(connect), Duration.ofSeconds(read));
    }

    private DocumentoElectronico documento() {
        return new DocumentoElectronico(TipoDocumentoElectronico.FACTURA_VENTA, "2", LocalDate.of(2026, 10, 23), "FEE", "COP",
                new AdquirenteElectronico("NATURAL", "CC", "123456789", null, null, "Cliente", null, "Prueba", null, null, null,
                        new DireccionFiscal("Calle de prueba", "001", "05", "CO")),
                List.of(new LineaDocumentoElectronico("Artículo", "Artículo", BigDecimal.ONE, "94", BigDecimal.TEN, BigDecimal.ZERO)), true, false);
    }
}
