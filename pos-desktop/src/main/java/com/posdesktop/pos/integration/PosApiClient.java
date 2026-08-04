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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PosApiClient {

    private static final Duration API_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration API_REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final String AUTH_HEADER = "X-Pos-Auth";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String authToken;

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

    public AuthSessionResponse login(String username, String password) {
        AuthSessionResponse session = post(
                "/auth/login",
                new LoginRequest(username, password),
                new TypeReference<ApiResponseEnvelope<AuthSessionResponse>>() {
                }
        ).data();
        this.authToken = session.token();
        return session;
    }

    public AuthSessionResponse consultarSesionActual() {
        return get("/auth/me", new TypeReference<ApiResponseEnvelope<AuthSessionResponse>>() {
        }).data();
    }

    public void logout() {
        try {
            post("/auth/logout", new EmptyRequest(), new TypeReference<ApiResponseEnvelope<Void>>() {
            });
        } finally {
            this.authToken = null;
        }
    }

    public void clearSession() {
        this.authToken = null;
    }

    public VentaRegistradaResponse registrarVenta(RegistrarVentaRequest request) {
        return post("/ventas", request, new TypeReference<ApiResponseEnvelope<VentaRegistradaResponse>>() {
        }).data();
    }

    public VentaRegistradaResponse consultarVenta(String ventaId) {
        return get("/ventas/" + encode(ventaId), new TypeReference<ApiResponseEnvelope<VentaRegistradaResponse>>() {
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

    public List<ProveedorResponse> listarProveedores() {
        return get("/proveedores", new TypeReference<ApiResponseEnvelope<List<ProveedorResponse>>>() {
        }).data();
    }

    public ProveedorResponse registrarProveedor(RegistrarProveedorRequest request) {
        return post("/proveedores", request, new TypeReference<ApiResponseEnvelope<ProveedorResponse>>() {
        }).data();
    }

    public ProveedorResponse actualizarProveedor(String proveedorId, ActualizarProveedorRequest request) {
        return put(
                "/proveedores/" + encode(proveedorId),
                request,
                new TypeReference<ApiResponseEnvelope<ProveedorResponse>>() {
                }
        ).data();
    }

    public List<FacturaProveedorListadoResponse> listarFacturas(String proveedorId, String estado) {
        StringBuilder path = new StringBuilder("/facturas-proveedor");
        boolean hasQuery = false;
        if (proveedorId != null && !proveedorId.isBlank()) {
            path.append(hasQuery ? "&" : "?").append("proveedorId=").append(encode(proveedorId));
            hasQuery = true;
        }
        if (estado != null && !estado.isBlank()) {
            path.append(hasQuery ? "&" : "?").append("estado=").append(encode(estado));
        }
        return get(path.toString(), new TypeReference<ApiResponseEnvelope<List<FacturaProveedorListadoResponse>>>() {
        }).data();
    }

    public FacturaProveedorDetalleResponse consultarFacturaProveedor(String facturaId) {
        return get(
                "/facturas-proveedor/" + encode(facturaId),
                new TypeReference<ApiResponseEnvelope<FacturaProveedorDetalleResponse>>() {
                }
        ).data();
    }

    public FacturaProveedorDetalleResponse registrarFacturaProveedor(
            RegistrarFacturaProveedorRequest request,
            List<Path> imagenes
    ) {
        if (imagenes == null || imagenes.isEmpty()) {
            return post("/facturas-proveedor", request, new TypeReference<ApiResponseEnvelope<FacturaProveedorDetalleResponse>>() {
            }).data();
        }
        return postMultipart(
                "/facturas-proveedor",
                "factura",
                request,
                "imagenes",
                imagenes,
                new TypeReference<ApiResponseEnvelope<FacturaProveedorDetalleResponse>>() {
                }
        ).data();
    }

    public FacturaProveedorDetalleResponse actualizarFacturaProveedor(
            String facturaId,
            ActualizarFacturaProveedorRequest request
    ) {
        return put(
                "/facturas-proveedor/" + encode(facturaId),
                request,
                new TypeReference<ApiResponseEnvelope<FacturaProveedorDetalleResponse>>() {
                }
        ).data();
    }

    public FacturaProveedorDetalleResponse registrarAbonoFactura(
            String facturaId,
            RegistrarPagoFacturaRequest request,
            List<Path> soportes
    ) {
        String path = "/facturas-proveedor/" + encode(facturaId) + "/abonos";
        if (soportes == null || soportes.isEmpty()) {
            return post(path, request, new TypeReference<ApiResponseEnvelope<FacturaProveedorDetalleResponse>>() {
            }).data();
        }
        return postMultipart(
                path,
                "abono",
                request,
                "soportes",
                soportes,
                new TypeReference<ApiResponseEnvelope<FacturaProveedorDetalleResponse>>() {
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
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(API_REQUEST_TIMEOUT)
                .GET()
                .header("Accept", "application/json");
        applyAuthHeader(builder);
        HttpRequest request = builder.build();
        return execute(request, typeReference);
    }

    private <T> ApiResponseEnvelope<T> post(
            String path,
            Object body,
            TypeReference<ApiResponseEnvelope<T>> typeReference
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(API_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            applyAuthHeader(builder);
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return execute(request, typeReference);
        } catch (IOException exception) {
            throw new PosApiException("No fue posible serializar la solicitud hacia la API.", exception);
        }
    }

    private <T> ApiResponseEnvelope<T> put(
            String path,
            Object body,
            TypeReference<ApiResponseEnvelope<T>> typeReference
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(API_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            applyAuthHeader(builder);
            HttpRequest request = builder
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return execute(request, typeReference);
        } catch (IOException exception) {
            throw new PosApiException("No fue posible serializar la solicitud hacia la API.", exception);
        }
    }

    private <T> ApiResponseEnvelope<T> postMultipart(
            String path,
            String jsonPartName,
            Object jsonBody,
            String filePartName,
            List<Path> files,
            TypeReference<ApiResponseEnvelope<T>> typeReference
    ) {
        String boundary = "----POSDesktopBoundary" + System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(API_REQUEST_TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json");
            applyAuthHeader(builder);
            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(buildMultipartPayload(
                            boundary,
                            jsonPartName,
                            jsonBody,
                            filePartName,
                            files
                    )))
                    .build();
            return execute(request, typeReference);
        } catch (IOException exception) {
            throw new PosApiException("No fue posible construir la solicitud multipart hacia la API.", exception);
        }
    }

    private void applyAuthHeader(HttpRequest.Builder builder) {
        if (authToken != null && !authToken.isBlank()) {
            builder.header(AUTH_HEADER, authToken);
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

    private Iterable<byte[]> buildMultipartPayload(
            String boundary,
            String jsonPartName,
            Object jsonBody,
            String filePartName,
            List<Path> files
    ) throws IOException {
        List<byte[]> payload = new ArrayList<>();
        String separator = "--" + boundary + "\r\n";
        String lineBreak = "\r\n";

        payload.add(separator.getBytes(StandardCharsets.UTF_8));
        payload.add(("Content-Disposition: form-data; name=\"" + jsonPartName + "\"" + lineBreak).getBytes(StandardCharsets.UTF_8));
        payload.add(("Content-Type: application/json; charset=UTF-8" + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
        payload.add(objectMapper.writeValueAsBytes(jsonBody));
        payload.add(lineBreak.getBytes(StandardCharsets.UTF_8));

        for (Path file : files) {
            if (file == null) {
                continue;
            }
            payload.add(separator.getBytes(StandardCharsets.UTF_8));
            payload.add((
                    "Content-Disposition: form-data; name=\"" + filePartName + "\"; filename=\"" + file.getFileName() + "\"" + lineBreak
            ).getBytes(StandardCharsets.UTF_8));
            payload.add(("Content-Type: " + detectContentType(file) + lineBreak + lineBreak).getBytes(StandardCharsets.UTF_8));
            payload.add(Files.readAllBytes(file));
            payload.add(lineBreak.getBytes(StandardCharsets.UTF_8));
        }

        payload.add(("--" + boundary + "--" + lineBreak).getBytes(StandardCharsets.UTF_8));
        return payload;
    }

    private String detectContentType(Path file) throws IOException {
        String detected = Files.probeContentType(file);
        if (detected != null && !detected.isBlank()) {
            return detected;
        }
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
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

    public record EmptyRequest() {
    }

    public record LoginRequest(
            String username,
            String password
    ) {
    }

    public record AuthSessionResponse(
            String token,
            LocalDateTime expiraEn,
            String usuarioId,
            String username,
            String nombreCompleto,
            List<String> roles,
            List<String> permisos
    ) {
    }

    public record RegistrarVentaRequest(
            List<RegistrarDetalleVentaRequest> detalles,
            BigDecimal montoRecibido,
            String observacion,
            String medioPago
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
            String observacion,
            String medioPago
    ) {
    }

    public record RegistrarAbonoSeparadoRequest(
            BigDecimal valorAbono,
            String observacion,
            String medioPago
    ) {
    }

    public record RegistrarProveedorRequest(
            String nit,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String observacion
    ) {
    }

    public record ActualizarProveedorRequest(
            String nit,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String observacion
    ) {
    }

    public record ProveedorResponse(
            String id,
            String nit,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String observacion,
            boolean activo,
            BigDecimal saldoPendienteTotal,
            int cantidadFacturas
    ) {
    }

    public record RegistrarFacturaProveedorRequest(
            String proveedorId,
            String numeroFactura,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            BigDecimal valorTotal,
            BigDecimal saldoInicial,
            String observacion
    ) {
    }

    public record ActualizarFacturaProveedorRequest(
            String numeroFactura,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            BigDecimal valorTotal,
            String observacion
    ) {
    }

    public record ProveedorFacturaResponse(
            String id,
            String nit,
            String nombre,
            String telefono,
            String correo
    ) {
    }

    public record DocumentoSoporteResponse(
            String id,
            String entidadOrigen,
            String entidadOrigenId,
            String tipoDocumento,
            String nombreArchivo,
            String contentType,
            Long tamanioBytes,
            String rutaArchivo,
            String rutaRelativa,
            List<String> carpetas,
            String checksum,
            String observacion,
            LocalDateTime cargadoEn
    ) {
    }

    public record PagoFacturaResponse(
            String id,
            LocalDate fechaPago,
            BigDecimal montoPago,
            String metodoPago,
            String referenciaPago,
            String observacion,
            BigDecimal saldoRestante,
            List<DocumentoSoporteResponse> soportes
    ) {
    }

    public record FacturaProveedorListadoResponse(
            String id,
            String proveedorId,
            String proveedorNombre,
            String proveedorNit,
            String numeroFactura,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String estado,
            BigDecimal montoTotal,
            BigDecimal montoPagado,
            BigDecimal saldoPendiente,
            String observacion
    ) {
    }

    public record FacturaProveedorDetalleResponse(
            String id,
            ProveedorFacturaResponse proveedor,
            String numeroFactura,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String estado,
            BigDecimal montoTotal,
            BigDecimal montoPagado,
            BigDecimal saldoPendiente,
            String observacion,
            List<DocumentoSoporteResponse> soportesFactura,
            List<PagoFacturaResponse> abonos
    ) {
    }

    public record RegistrarPagoFacturaRequest(
            LocalDate fechaPago,
            BigDecimal valorAbono,
            String metodoPago,
            String referenciaPago,
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
            String observacion,
            String responsableUsuario,
            String medioPago
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
            LocalDate fechaSeparacion,
            String responsableUsuario
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
            String estado,
            String responsableUsuario
    ) {
    }

    public record MovimientoVentaResponse(
            String id,
            String numeroVenta,
            String origen,
            BigDecimal total,
            BigDecimal montoRecibido,
            BigDecimal cambioEntregado,
            LocalDateTime fechaVenta,
            String medioPago
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
