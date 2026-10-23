package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataicoPdfDownloaderTest {
    @TempDir Path carpeta;
    private HttpServer server;
    private String baseUrl;
    private final DataicoPdfDownloader downloader = new DataicoPdfDownloader();

    @BeforeEach
    void iniciarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void detenerServidor() {
        server.stop(0);
    }

    @Test
    void descargaLosBytesOriginalesYResuelveRedireccionSinCredenciales() throws Exception {
        byte[] original = "%PDF-1.7\nDocumento de Dataico\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        AtomicInteger peticionesSinCredenciales = new AtomicInteger();
        server.createContext("/enlace", exchange -> {
            if (exchange.getRequestHeaders().getFirst("X-Pos-Auth") == null
                    && exchange.getRequestHeaders().getFirst("Authorization") == null) {
                peticionesSinCredenciales.incrementAndGet();
            }
            exchange.getResponseHeaders().set("Location", baseUrl + "/original.pdf");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/original.pdf", exchange -> {
            if (exchange.getRequestHeaders().getFirst("X-Pos-Auth") == null
                    && exchange.getRequestHeaders().getFirst("Authorization") == null) {
                peticionesSinCredenciales.incrementAndGet();
            }
            responder(exchange, 200, original);
        });
        Path archivo = carpeta.resolve("factura.pdf");

        assertEquals(archivo, downloader.descargar(baseUrl + "/enlace", archivo));

        assertArrayEquals(original, Files.readAllBytes(archivo));
        assertEquals(2, peticionesSinCredenciales.get());
        try (var archivos = Files.list(carpeta)) {
            assertEquals(1, archivos.count());
        }
    }

    @Test
    void noSobrescribeElArchivoSiElServidorDevuelveUnError() throws Exception {
        server.createContext("/error", exchange -> responder(exchange, 503, "No disponible".getBytes()));
        Path archivo = carpeta.resolve("existente.pdf");
        byte[] anterior = "%PDF-1.4\nDocumento anterior".getBytes(StandardCharsets.US_ASCII);
        Files.write(archivo, anterior);

        IOException error = assertThrows(IOException.class,
                () -> downloader.descargar(baseUrl + "/error", archivo));

        assertEquals("No se pudo descargar el PDF de Dataico (HTTP 503).", error.getMessage());
        assertArrayEquals(anterior, Files.readAllBytes(archivo));
    }

    @Test
    void rechazaHtmlAunqueElEstadoSeaExitoso() throws Exception {
        server.createContext("/html", exchange -> responder(exchange, 200, "<html>Error</html>".getBytes()));
        Path archivo = carpeta.resolve("factura.pdf");

        assertThrows(IOException.class, () -> downloader.descargar(baseUrl + "/html", archivo));

        try (var archivos = Files.list(carpeta)) {
            assertEquals(0, archivos.count());
        }
    }

    @Test
    void rechazaEnlacesQueNoSeanHttpSinCrearArchivos() {
        assertThrows(IOException.class, () -> downloader.descargar("file:///original.pdf", carpeta.resolve("factura.pdf")));
    }

    private void responder(HttpExchange exchange, int estado, byte[] bytes) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(estado, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
