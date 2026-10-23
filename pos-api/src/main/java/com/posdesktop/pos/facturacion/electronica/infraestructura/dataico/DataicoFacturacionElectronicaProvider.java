package com.posdesktop.pos.facturacion.electronica.infraestructura.dataico;

import com.posdesktop.pos.facturacion.electronica.application.ProveedorFacturacionElectronica;
import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.facturacion.electronica.domain.TerceroDian;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adaptador de infraestructura para la API REST v2 de Dataico. */
public class DataicoFacturacionElectronicaProvider implements ProveedorFacturacionElectronica {
    private static final Logger log = LoggerFactory.getLogger(DataicoFacturacionElectronicaProvider.class);
    private static final String INVOICES_PATH = "/invoices";
    private static final String DIAN_THIRD_PARTIES_PATH = "/dian_terceros";
    private static final List<String> DIAN_IDENTIFICATION_TYPES = List.of(
            "CC", "CE", "NIT", "PASAPORTE", "TI", "RC", "NUIP", "TE", "PEP", "PPT", "IE", "NIT_OTRO_PAIS"
    );
    private final DataicoInvoiceMapper mapper;
    private final java.util.function.Supplier<DataicoProperties> configuracion;
    private final java.util.function.Function<DataicoProperties, RestClient> clientFactory;

    public DataicoFacturacionElectronicaProvider(RestClient restClient, DataicoProperties properties, DataicoInvoiceMapper mapper) {
        this(restClient, properties, mapper, properties::tokenParaProveedor);
    }

    public DataicoFacturacionElectronicaProvider(RestClient restClient, DataicoProperties properties, DataicoInvoiceMapper mapper,
            java.util.function.Supplier<String> tokenSupplier) {
        this(mapper, () -> new DataicoProperties(properties.enabled(), properties.baseUrl(), properties.accountId(), null,
                tokenSupplier.get(), properties.prefix(), properties.resolutionNumber(), properties.initialNumber(),
                properties.flexible(), properties.environment(), properties.connectTimeout(), properties.readTimeout()), current -> restClient);
    }

    public DataicoFacturacionElectronicaProvider(DataicoInvoiceMapper mapper,
            java.util.function.Supplier<DataicoProperties> configuracion,
            java.util.function.Function<DataicoProperties, RestClient> clientFactory) {
        this.mapper = mapper;
        this.configuracion = configuracion;
        this.clientFactory = clientFactory;
    }

    @Override
    public String codigoProveedor() {
        return "dataico";
    }

    @Override
    public ResultadoEmisionDocumento emitir(DocumentoElectronico documento) {
        try {
            Conexion conexion = conexion();
            Map<String, Object> payload = mapper.toRequest(documento, conexion.properties());
            Map<?, ?> invoice = (Map<?, ?>) payload.get("invoice");
            Map<?, ?> numbering = (Map<?, ?>) invoice.get("numbering");
            log.info("Enviando factura {} a Dataico (prefijo={}, resolución={}, ambiente={}).",
                    documento.numero(), numbering.get("prefix"), numbering.get("resolution_number"), invoice.get("env"));
            Map<String, Object> response = requestPost(INVOICES_PATH, payload, conexion);
            ResultadoEmisionDocumento resultado = toResultado(response, documento.numero());
            log.info("Respuesta recibida de Dataico para factura {}: estado={}", documento.numero(), resultado.estado());
            return resultado;
        } catch (RestClientResponseException exception) {
            String detalle = detalleRespuesta(exception);
            log.warn("Dataico rechazó la factura {} con HTTP {}: {}", documento.numero(), exception.getStatusCode(), detalle);
            throw new DataicoApiException(
                    "Dataico respondió " + exception.getStatusCode().value() + ": " + detalle,
                    exception
            );
        } catch (RestClientException exception) {
            log.warn("Dataico rechazó o no respondió a la factura {}: {}", documento.numero(), exception.getMessage());
            throw new DataicoApiException("No fue posible emitir la factura en Dataico.", exception);
        }
    }

    private String detalleRespuesta(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return exception.getStatusText() == null ? "sin detalle" : exception.getStatusText();
        }
        String compacta = body.replaceAll("\\s+", " ").trim();
        return compacta.length() <= 600 ? compacta : compacta.substring(0, 600) + "…";
    }

    @Override
    public ResultadoEmisionDocumento consultarFactura(String numeroFactura) {
        if (numeroFactura == null || numeroFactura.isBlank()) {
            throw new IllegalArgumentException("El numero de factura es obligatorio.");
        }
        try {
            Conexion conexion = conexion();
            Map<String, Object> response = conexion.client().get().uri(uriBuilder -> uriBuilder.path(INVOICES_PATH)
                            .queryParam("number", numeroFactura).build())
                    .headers(headers -> authenticationHeaders(headers, conexion.properties())).retrieve().body(Map.class);
            return toResultado(response, numeroFactura);
        } catch (RestClientException exception) {
            throw new DataicoApiException("No fue posible consultar la factura en Dataico.", exception);
        }
    }

    @Override
    public TerceroDian consultarTerceroDian(String tipoIdentificacion, String identificacion) {
        return consultarTerceroDian(tipoIdentificacion, identificacion, conexion());
    }

    private TerceroDian consultarTerceroDian(String tipoIdentificacion, String identificacion, Conexion conexion) {
        if (tipoIdentificacion == null || tipoIdentificacion.isBlank() || identificacion == null || identificacion.isBlank()) {
            throw new IllegalArgumentException("El tipo y número de identificación son obligatorios para consultar DIAN.");
        }
        try {
            Map<String, Object> response = conexion.client().get().uri(uriBuilder -> uriBuilder.path(DIAN_THIRD_PARTIES_PATH)
                            .queryParam("identification_type", tipoIdentificacion.trim())
                            .queryParam("identification", identificacion.trim()).build())
                    .headers(headers -> authenticationHeaders(headers, conexion.properties())).retrieve().body(Map.class);
            return toTerceroDian(response, tipoIdentificacion, identificacion);
        } catch (RestClientException exception) {
            throw new DataicoApiException("No fue posible consultar el tercero en DIAN mediante Dataico.", exception);
        }
    }

    @Override
    public TerceroDian consultarTerceroDian(String identificacion) {
        if (identificacion == null || identificacion.isBlank()) {
            throw new IllegalArgumentException("El número de identificación es obligatorio para consultar DIAN.");
        }
        TerceroDian mejorCoincidencia = null;
        int mejorPuntaje = -1;
        DataicoApiException ultimoError = null;
        Conexion conexion = conexion();
        for (String tipoIdentificacion : DIAN_IDENTIFICATION_TYPES) {
            try {
                TerceroDian tercero = consultarTerceroDian(tipoIdentificacion, identificacion, conexion);
                int puntaje = puntajeDatosTercero(tercero);
                if (puntaje > mejorPuntaje) {
                    mejorCoincidencia = tercero;
                    mejorPuntaje = puntaje;
                }
                if (puntaje >= 3) {
                    return tercero;
                }
            } catch (DataicoApiException exception) {
                ultimoError = exception;
            }
        }
        if (mejorCoincidencia != null && mejorPuntaje > 0) {
            return mejorCoincidencia;
        }
        if (ultimoError != null) {
            throw ultimoError;
        }
        throw new IllegalArgumentException("No se encontró información del tercero en DIAN.");
    }

    @Override
    public void reenviar(String identificadorExterno, boolean enviarDian, boolean enviarEmail, String email) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            throw new IllegalArgumentException("El identificador externo de la factura es obligatorio.");
        }
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("send_dian", enviarDian);
        actions.put("send_email", enviarEmail);
        if (email != null && !email.isBlank()) {
            actions.put("email", email);
        }
        try {
            Conexion conexion = conexion();
            log.info("Solicitando reenvío de factura {} en Dataico (DIAN={}, correo={}).",
                    identificadorExterno, enviarDian, enviarEmail);
            conexion.client().put().uri(INVOICES_PATH + "/{uuid}", identificadorExterno)
                    .contentType(MediaType.APPLICATION_JSON).headers(headers -> authenticationHeaders(headers, conexion.properties()))
                    .body(Map.of("actions", actions)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new DataicoApiException("Dataico respondió " + exception.getStatusCode().value()
                    + ": " + detalleRespuesta(exception), exception);
        } catch (RestClientException exception) {
            throw new DataicoApiException("No fue posible reenviar la factura en Dataico.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestPost(String path, Object body, Conexion conexion) {
        return conexion.client().post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> authenticationHeaders(headers, conexion.properties())).body(body).retrieve().body(Map.class);
    }

    private Conexion conexion() {
        DataicoProperties current = configuracion.get();
        if (!current.enabled()) throw new IllegalArgumentException("Dataico está deshabilitado. Habilítalo desde Configuración Dataico.");
        if (current.tokenParaProveedor() == null || current.tokenParaProveedor().isBlank()) {
            throw new IllegalArgumentException("Configura el token de Dataico desde Configuración Dataico antes de continuar.");
        }
        return new Conexion(current, clientFactory.apply(current));
    }

    private record Conexion(DataicoProperties properties, RestClient client) { }

    private void authenticationHeaders(org.springframework.http.HttpHeaders headers, DataicoProperties properties) {
        String token = properties.tokenParaProveedor();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Configura el token de Dataico desde Configuración Dataico antes de continuar.");
        }
        headers.set("Auth-token", token);
        if (properties.accountId() != null && !properties.accountId().isBlank()) {
            headers.set("Dataico_account_id", properties.accountId());
            headers.set("x-on-behalf-of", properties.accountId());
        }
    }

    @SuppressWarnings("unchecked")
    private ResultadoEmisionDocumento toResultado(Map<String, Object> body, String fallbackNumero) {
        Map<String, Object> source = body != null && body.get("data") instanceof Map<?, ?> data
                ? (Map<String, Object>) data : body;
        if (source != null && source.get("invoice") instanceof Map<?, ?> invoice) {
            source = (Map<String, Object>) invoice;
        }
        return new ResultadoEmisionDocumento(
                codigoProveedor(), texto(source, "uuid", "id"), texto(source, "number", fallbackNumero),
                texto(source, "dian_status", "status"), texto(source, "cufe", "CUFE"),
                texto(source, "qrcode", "qr_code"), texto(source, "pdf_url", "pdf", "pdf_link"),
                texto(source, "message", "detail"), texto(source, "email_status")
        );
    }

    @SuppressWarnings("unchecked")
    private TerceroDian toTerceroDian(Map<String, Object> body, String fallbackType, String fallbackIdentification) {
        Map<String, Object> source = source(body);
        Map<String, Object> address = source.get("address") instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
        String documentType = normalizarTipoIdentificacion(
                texto(source, "document_type", "identification_type", "type_document", fallbackType)
        );
        String partyType = texto(source, "party_type", "type_person", "person_type", "person_nature");
        return new TerceroDian(
                esPersonaNatural(partyType, documentType) ? "NATURAL" : "JURIDICA",
                documentType,
                texto(source, "party_identification", "identification", "document_number", fallbackIdentification),
                texto(source, "check_digit", "verification_digit", "dv"),
                texto(source, "company_name", "business_name", "name"),
                texto(source, "first_name", "given_name", "primer_nombre"),
                texto(source, "other_names", "second_name", "segundo_nombre"),
                texto(source, "family_name", "last_name", "first_surname", "primer_apellido"),
                texto(source, "second_last_name", "second_surname", "segundo_apellido"),
                texto(source, "email", "electronic_mail"), texto(source, "phone", "telephone"),
                texto(address, "address_line", "address"), texto(address, "city", "city_code"),
                texto(address, "department", "department_code"), texto(address, "country", "country_code")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> source(Map<String, Object> body) {
        if (body == null) return Map.of();
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map) return (Map<String, Object>) map;
        if (data instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return body;
    }

    private String texto(Map<String, Object> node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            if (node.containsKey(key) && node.get(key) != null) return String.valueOf(node.get(key));
        }
        return null;
    }

    private boolean esPersonaNatural(String partyType, String documentType) {
        if (partyType != null && !partyType.isBlank()) {
            String normalizado = partyType.trim().toUpperCase(java.util.Locale.ROOT);
            return normalizado.contains("PERSON") || normalizado.contains("NATURAL");
        }
        return documentType != null && !documentType.equalsIgnoreCase("NIT") && !documentType.equalsIgnoreCase("NIT_OTRO_PAIS");
    }

    private int puntajeDatosTercero(TerceroDian tercero) {
        int puntaje = 0;
        if (tieneTexto(tercero.razonSocial())) puntaje += 3;
        if (tieneTexto(tercero.primerNombre())) puntaje++;
        if (tieneTexto(tercero.primerApellido())) puntaje += 2;
        if (tieneTexto(tercero.segundoNombre())) puntaje++;
        if (tieneTexto(tercero.segundoApellido())) puntaje++;
        return puntaje;
    }

    private boolean tieneTexto(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizarTipoIdentificacion(String tipo) {
        if (tipo == null || tipo.isBlank()) return tipo;
        String normalizado = java.text.Normalizer.normalize(tipo, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizado.contains("CEDULA DE CIUDADANIA")) return "CC";
        if (normalizado.contains("CEDULA DE EXTRANJERIA")) return "CE";
        if (normalizado.contains("TARJETA DE IDENTIDAD")) return "TI";
        if (normalizado.contains("REGISTRO CIVIL")) return "RC";
        if (normalizado.contains("PASAPORTE")) return "PASAPORTE";
        if (normalizado.contains("PERMISO") && normalizado.contains("PEP")) return "PEP";
        if (normalizado.contains("PERMISO") && normalizado.contains("PPT")) return "PPT";
        if (normalizado.contains("NIT") && normalizado.contains("OTRO")) return "NIT_OTRO_PAIS";
        if (normalizado.equals("NIT")) return "NIT";
        return normalizado;
    }
}
