package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PosApiClientFacturacionTest {
    private HttpServer server;
    private PosApiClient cliente;

    @BeforeEach
    void iniciarApiLocal() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        cliente = new PosApiClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void detenerApiLocal() {
        server.stop(0);
    }

    @Test
    void esperaLaRespuestaDeDataicoAunqueSupereCuatroSegundosYLaDianRechace() {
        AtomicInteger solicitudes = new AtomicInteger();
        server.createContext("/ventas/venta-prueba/factura-electronica", exchange -> {
            solicitudes.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            }
            responder(exchange, 200, """
                    {"success":true,"message":"Factura enviada a Dataico.","data":{
                      "identificadorExterno":"dataico-prueba",
                      "numero":"1789500096",
                      "estadoDian":"DIAN_RECHAZADO",
                      "pdfUrl":"https://documentos.example.test/original.pdf",
                      "mensaje":"El documento se creó en Dataico."
                    }}
                    """);
        });

        PosApiClient.ResultadoFacturaElectronicaResponse respuesta = assertTimeoutPreemptively(
                Duration.ofSeconds(15), () -> cliente.emitirFacturaElectronica("venta-prueba", "cliente-prueba"));

        assertEquals("dataico-prueba", respuesta.identificadorExterno());
        assertEquals("1789500096", respuesta.numero());
        assertEquals("DIAN_RECHAZADO", respuesta.estadoDian());
        assertEquals("https://documentos.example.test/original.pdf", respuesta.pdfUrl());
        assertEquals(1, solicitudes.get());
    }

    @Test
    void conservaElErrorCuandoElEnvioEsRechazadoPorLaApi() {
        server.createContext("/ventas/venta-prueba/factura-electronica", exchange -> {
            exchange.getRequestBody().readAllBytes();
            responder(exchange, 500, """
                    {"success":false,"message":"Dataico rechazó la solicitud.","details":["Resolución inválida."]}
                    """);
        });

        PosApiClient.PosApiException error = assertThrows(PosApiClient.PosApiException.class,
                () -> cliente.emitirFacturaElectronica("venta-prueba", "cliente-prueba"));

        assertTrue(error.getMessage().contains("Resolución inválida"));
    }

    @Test
    void enviaElDestinatarioEditadoALaAccionDeCorreoDeLaFacturaExistente() {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/ventas/facturas-electronicas/factura-existente/correo", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(exchange, 200, """
                    {"success":true,"data":{"id":"factura-existente","estadoCorreo":"ENVIADO",
                    "clienteEmail":"nuevo@example.test"}}
                    """);
        });

        var resultado = cliente.enviarCorreoFactura("factura-existente", "nuevo@example.test");

        assertTrue(body.get().contains("\"email\":\"nuevo@example.test\""));
        assertEquals("nuevo@example.test", resultado.clienteEmail());
        assertTrue(resultado.correoEnviado());
    }

    @Test
    void solicitaEnvioDianSinCrearUnaVentaNiUnaFacturaNueva() {
        server.createContext("/ventas/facturas-electronicas/factura-existente/dian", exchange -> {
            exchange.getRequestBody().readAllBytes();
            responder(exchange, 200, """
                    {"success":true,"data":{"id":"factura-existente","estadoDian":"DIAN_ACEPTADO"}}
                    """);
        });

        var resultado = cliente.enviarFacturaDian("factura-existente");

        assertEquals("factura-existente", resultado.id());
        assertTrue(resultado.dianAceptada());
    }

    private void responder(HttpExchange exchange, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(estado, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
