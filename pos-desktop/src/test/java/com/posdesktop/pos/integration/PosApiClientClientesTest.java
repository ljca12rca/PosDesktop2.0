package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PosApiClientClientesTest {
    @Test
    void seleccionaElConsumidorFinalPersistidoPorDocumentoExacto() throws IOException {
        conApiClientes("""
                {"success":true,"data":[
                  {"id":"otro","tipoIdentificacion":"CC","identificacion":"1222222222222"},
                  {"id":"","tipoIdentificacion":"CC","identificacion":"222222222222"},
                  {"id":"cliente-existente","tipoIdentificacion":"CC","tipoPersona":"NATURAL",
                   "identificacion":"222222222222","razonSocial":"CONSUMIDOR FINAL"}
                ]}
                """, cliente -> {
            PosApiClient.ClienteElectronicoResponse consumidor = cliente.buscarConsumidorFinal();
            assertEquals("cliente-existente", consumidor.id());
            assertEquals("222222222222", consumidor.identificacion());
            assertEquals("CONSUMIDOR FINAL", consumidor.nombreFactura());
        });
    }

    @Test
    void noInventaUnClienteSiNoExisteEnLaBase() throws IOException {
        conApiClientes("{\"success\":true,\"data\":[]}", cliente -> {
            PosApiClient.PosApiException error = assertThrows(PosApiClient.PosApiException.class,
                    cliente::buscarConsumidorFinal);
            assertEquals("El cliente Consumidor final no está disponible.", error.getMessage());
        });
    }

    private void conApiClientes(String respuesta, java.util.function.Consumer<PosApiClient> prueba) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/clientes-electronicos", exchange -> {
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            prueba.accept(new PosApiClient("http://127.0.0.1:" + server.getAddress().getPort()));
        } finally {
            server.stop(0);
        }
    }
}
