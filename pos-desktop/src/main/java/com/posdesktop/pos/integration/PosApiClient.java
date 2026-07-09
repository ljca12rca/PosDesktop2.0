package com.posdesktop.pos.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;

public final class PosApiClient {

    private static final Duration API_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration API_REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PosApiClient(String baseUrl) {
        this.baseUrl = quitarSlashFinal(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(API_CONNECT_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public static PosApiClient createDefault() {
        return new PosApiClient(PosDesktopConfig.load().apiBaseUrl());
    }

    public VentaRegistradaResponse registrarVenta(RegistrarVentaRequest request) {
        return post("/ventas", request, new TypeReference<ApiResponseEnvelope<VentaRegistradaResponse>>() {
        }).data();
    }

    public List<SeparadoListadoResponse> listarSeparados(String estado, String articulo) {
        StringBuilder path = new StringBuilder("/separados");
        boolean hasQuery = false;
        if (estado != null && !estado.isBlank()) {
            path.append(hasQuery ? "&" : "?").append("estado=").append(encode(estado));
            hasQuery = true;
        }
        if (articulo != null && !articulo.isBlank()) {
            path.append(hasQuery ? "&" : "?").append("articulo=").append(encode(articulo.trim()));
        }
        return get(path.toString(), new TypeReference<ApiResponseEnvelope<List<SeparadoListadoResponse>>>() {
        }).data();
    }

    public SeparadoDetalleResponse consultarSeparado(String separadoId) {
        return get("/separados/" + encode(separadoId), new TypeReference<ApiResponseEnvelope<SeparadoDetalleResponse>>() {
        }).data();
    }

    public SeparadoDetalleResponse registrarSeparado(RegistrarSeparadoRequest request) {
        return post("/separados", request, new TypeReference<ApiResponseEnvelope<SeparadoDetalleResponse>>() {
        }).data();
    }

    public SeparadoDetalleResponse registrarAbonoSeparado(String separadoId, RegistrarAbonoSeparadoRequest request) {
        return post(
                "/separados/" + encode(separadoId) + "/abonos",
                request,
                new TypeReference<ApiResponseEnvelope<SeparadoDetalleResponse>>() {
                }
        ).data();
    }

    public SystemStatusResponse consultarEstadoSistema() {
        return get("/system/ping", new TypeReference<ApiResponseEnvelope<SystemStatusResponse>>() {
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

    public String baseUrl() {
        return baseUrl;
    }

    private <T> ApiResponseEnvelope<T> get(String path, TypeReference<ApiResponseEnvelope<T>> typeReference) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(API_REQUEST_TIMEOUT)
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
                    .timeout(API_REQUEST_TIMEOUT)
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

            try {
                ApiErrorResponse error = objectMapper.readValue(response.body(), ApiErrorResponse.class);
                String details = error.details() == null || error.details().isEmpty()
                        ? error.message()
                        : String.join(" | ", error.details());
                throw new PosApiException(details);
            } catch (IOException ignored) {
                throw new PosApiException("La API respondio con estado " + response.statusCode() + ".");
            }
        } catch (ConnectException exception) {
            throw new PosApiException("No fue posible conectar con la API en " + baseUrl + ".", exception);
        } catch (HttpTimeoutException exception) {
            throw new PosApiException("La API no respondio a tiempo. Verifica si el servicio esta encendido.", exception);
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

    public record RegistrarSeparadoRequest(
            String cliente,
            String telefonoCliente,
            String descripcionArticulos,
            BigDecimal valorTotal,
            BigDecimal abonoInicial,
            String observacion
    ) {
    }

    public record RegistrarAbonoSeparadoRequest(
            BigDecimal valorAbono,
            String observacion
    ) {
    }

    public record AbonoSeparadoResponse(
            String id,
            Integer numeroAbono,
            LocalDateTime fechaAbono,
            BigDecimal montoAbono,
            boolean abonoInicial,
            String numeroVenta,
            String observacion
    ) {
    }

    public record SeparadoListadoResponse(
            String id,
            String numeroSeparado,
            String cliente,
            String descripcionArticulos,
            String estado,
            BigDecimal valorTotal,
            BigDecimal totalAbonado,
            BigDecimal saldoPendiente,
            LocalDate fechaSeparacion
    ) {
    }

    public record SeparadoDetalleResponse(
            String id,
            String numeroSeparado,
            String cliente,
            String telefonoCliente,
            String descripcionArticulos,
            String estado,
            BigDecimal valorTotal,
            BigDecimal montoMinimoInicial,
            BigDecimal totalAbonado,
            BigDecimal saldoPendiente,
            LocalDate fechaSeparacion,
            LocalDate fechaEntrega,
            String observacion,
            List<AbonoSeparadoResponse> abonos
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

    public record SystemStatusResponse(
            String application,
            String version,
            String environment,
            boolean desktopMockEnabled,
            List<ModuleStatusResponse> modules
    ) {
    }

    public record ModuleStatusResponse(
            String code,
            String name,
            String basePath,
            String stage,
            String summary
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
