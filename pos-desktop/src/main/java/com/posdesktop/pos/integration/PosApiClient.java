package com.posdesktop.pos.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class PosApiClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PosApiClient(String baseUrl) {
        this.baseUrl = quitarSlashFinal(baseUrl);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public static PosApiClient createDefault() {
        return new PosApiClient(PosDesktopConfig.load().apiBaseUrl());
    }

    public VentaRegistradaResponse registrarVenta(RegistrarVentaRequest request) {
        return post("/ventas", request, new TypeReference<ApiResponseEnvelope<VentaRegistradaResponse>>() {
        }).data();
    }

    public ResumenCierreDiarioResponse consultarResumenCierre(LocalDate fecha) {
        String path = "/cierres/resumen?fecha=" + encode(fecha.toString());
        return get(path, new TypeReference<ApiResponseEnvelope<ResumenCierreDiarioResponse>>() {
        }).data();
    }

    public ResumenCierreDiarioResponse registrarCierre(RegistrarCierreRequest request) {
        return post("/cierres", request, new TypeReference<ApiResponseEnvelope<ResumenCierreDiarioResponse>>() {
        }).data();
    }

    public List<CierreDiarioListadoResponse> listarCierres(LocalDate fechaInicial, LocalDate fechaFinal) {
        String path = "/cierres?fechaInicial=" + encode(fechaInicial.toString())
                + "&fechaFinal=" + encode(fechaFinal.toString());
        return get(path, new TypeReference<ApiResponseEnvelope<List<CierreDiarioListadoResponse>>>() {
        }).data();
    }

    public List<MovimientoVentaResponse> listarMovimientos(LocalDate fechaInicial, LocalDate fechaFinal) {
        String path = "/ventas/movimientos?fechaInicial=" + encode(fechaInicial.toString())
                + "&fechaFinal=" + encode(fechaFinal.toString());
        return get(path, new TypeReference<ApiResponseEnvelope<List<MovimientoVentaResponse>>>() {
        }).data();
    }

    private <T> ApiResponseEnvelope<T> get(String path, TypeReference<ApiResponseEnvelope<T>> typeReference) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .header("Accept", "application/json")
                .build();
        return execute(request, typeReference);
    }

    private <T> ApiResponseEnvelope<T> post(
            String path,
            Object body,
            TypeReference<ApiResponseEnvelope<T>> typeReference
    ) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return execute(request, typeReference);
        } catch (IOException exception) {
            throw new PosApiException("No fue posible serializar la solicitud hacia la API.", exception);
        }
    }

    private <T> ApiResponseEnvelope<T> execute(
            HttpRequest request,
            TypeReference<ApiResponseEnvelope<T>> typeReference
    ) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), typeReference);
            }

            ApiErrorResponse error = objectMapper.readValue(response.body(), ApiErrorResponse.class);
            String details = error.details() == null || error.details().isEmpty()
                    ? error.message()
                    : String.join(" | ", error.details());
            throw new PosApiException(details);
        } catch (IOException exception) {
            throw new PosApiException("No fue posible leer la respuesta de la API.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PosApiException("La llamada a la API fue interrumpida.", exception);
        }
    }

    private String quitarSlashFinal(String valor) {
        if (valor.endsWith("/")) {
            return valor.substring(0, valor.length() - 1);
        }
        return valor;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record ApiResponseEnvelope<T>(
            boolean success,
            String message,
            String path,
            OffsetDateTime timestamp,
            T data
    ) {
    }

    public record ApiErrorResponse(
            boolean success,
            String message,
            String path,
            OffsetDateTime timestamp,
            List<String> details
    ) {
    }

    public record RegistrarVentaRequest(
            List<RegistrarDetalleVentaRequest> detalles,
            BigDecimal montoRecibido,
            String observacion
    ) {
    }

    public record RegistrarDetalleVentaRequest(
            String descripcion,
            BigDecimal cantidad,
            BigDecimal valorUnitario
    ) {
    }

    public record DetalleVentaResponse(
            String id,
            int orden,
            String descripcion,
            BigDecimal cantidad,
            BigDecimal valorUnitario,
            BigDecimal subtotal,
            BigDecimal total
    ) {
    }

    public record VentaRegistradaResponse(
            String id,
            String numeroVenta,
            LocalDateTime fechaVenta,
            BigDecimal subtotal,
            BigDecimal total,
            BigDecimal montoRecibido,
            BigDecimal cambioEntregado,
            int cantidadDetalles,
            List<DetalleVentaResponse> detalles
    ) {
    }

    public record RegistrarCierreRequest(
            LocalDate fechaOperacion,
            BigDecimal base,
            BigDecimal trabajadoras,
            BigDecimal ahorro,
            String observacion
    ) {
    }

    public record ResumenCierreDiarioResponse(
            LocalDate fechaOperacion,
            int cantidadVentas,
            BigDecimal subtotalVentas,
            BigDecimal totalVentas,
            BigDecimal montoRecibido,
            BigDecimal cambioEntregado,
            BigDecimal montoNetoCaja,
            BigDecimal baseCaja,
            BigDecimal trabajadoras,
            BigDecimal ahorro,
            BigDecimal totalFinal,
            boolean cierreGuardado,
            String estado,
            String observacion
    ) {
    }

    public record CierreDiarioListadoResponse(
            String id,
            LocalDate fechaOperacion,
            LocalDateTime fechaHoraCierre,
            int cantidadVentas,
            BigDecimal totalVentas,
            BigDecimal montoNetoCaja,
            BigDecimal baseCaja,
            BigDecimal trabajadoras,
            BigDecimal ahorro,
            BigDecimal totalFinal,
            String estado
    ) {
    }

    public record MovimientoVentaResponse(
            String id,
            String numeroVenta,
            String origen,
            BigDecimal total,
            BigDecimal montoRecibido,
            BigDecimal cambioEntregado,
            LocalDateTime fechaVenta
    ) {
    }

    public static final class PosApiException extends RuntimeException {

        public PosApiException(String message) {
            super(message);
        }

        public PosApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
