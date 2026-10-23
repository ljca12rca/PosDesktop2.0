package com.posdesktop.pos.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Descarga el documento original sin enviar credenciales del POS al servidor del PDF. */
public final class DataicoPdfDownloader {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Path descargar(String url, Path destino) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IOException("La dirección del PDF de Dataico no es válida.");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/pdf")
                .GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("No se pudo descargar el PDF de Dataico (HTTP " + response.statusCode() + ").");
        }
        byte[] pdf = response.body();
        // No guardar páginas de error o JSON como si fueran el comprobante.
        if (pdf.length < 5 || !"%PDF-".equals(new String(pdf, 0, 5, StandardCharsets.US_ASCII))) {
            throw new IOException("Dataico aún no entregó un documento PDF válido.");
        }
        Path archivo = destino.toAbsolutePath();
        Path temporal = Files.createTempFile(archivo.getParent(), ".dataico-", ".tmp");
        try {
            Files.write(temporal, pdf);
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
            return archivo;
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
