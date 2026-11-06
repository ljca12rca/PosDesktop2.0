package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PosApiClientArticulosTest {
    @Test
    void consultaTextoYPrecioYDeserializaSugerenciasSinEnviarEscrituras() throws Exception {
        var consulta = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/articulos/sugerencias", exchange -> {
            consulta.set(exchange.getRequestMethod() + " " + URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"success":true,"data":[{"id":"articulo-1","nombre":"Café & pan","precioBase":3000.00}]}
                    """.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var client = new PosApiClient("http://127.0.0.1:" + server.getAddress().getPort());
            var items = client.sugerirArticulos("Café & pan", new BigDecimal("3000.00"));
            assertEquals("GET q=Café & pan&precio=3000.00", consulta.get());
            assertEquals("Café & pan", items.getFirst().nombre());
            assertEquals(new BigDecimal("3000.00"), items.getFirst().precioBase());
            client.sugerirArticulos("", new BigDecimal("3000"));
            assertEquals("GET q=&precio=3000", consulta.get());
        } finally { server.stop(0); }
    }
}
