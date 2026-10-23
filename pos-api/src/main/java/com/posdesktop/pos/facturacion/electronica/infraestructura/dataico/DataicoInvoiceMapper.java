package com.posdesktop.pos.facturacion.electronica.infraestructura.dataico;

import com.posdesktop.pos.facturacion.electronica.domain.AdquirenteElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.LineaDocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.TipoDocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Traduce el modelo propio al JSON de Dataico; no contiene llamadas HTTP. */
@Component
public class DataicoInvoiceMapper {
    private static final DateTimeFormatter FECHA_DATAICO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DataicoProperties properties;

    public DataicoInvoiceMapper(DataicoProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> toRequest(DocumentoElectronico documento) {
        return toRequest(documento, properties);
    }

    public Map<String, Object> toRequest(DocumentoElectronico documento, DataicoProperties current) {
        if (documento.tipo() != TipoDocumentoElectronico.FACTURA_VENTA) {
            throw new IllegalArgumentException("El adaptador Dataico actual solo admite facturas de venta.");
        }
        validar(documento);
        current.validarConsecutivo(Long.parseLong(documento.numero()));

        Map<String, Object> invoice = new LinkedHashMap<>();
        if (current.accountId() != null && !current.accountId().isBlank()) {
            invoice.put("dataico_account_id", current.accountId().trim());
        }
        String ambiente = documento.ajustes() == null ? current.environment() : documento.ajustes().ambiente();
        invoice.put("env", ambiente == null || ambiente.isBlank() ? "PRODUCCION" : ambiente.trim());
        invoice.put("number", documento.numero());
        invoice.put("issue_date", FECHA_DATAICO.format(documento.fechaEmision()));
        invoice.put("payment_date", FECHA_DATAICO.format(documento.fechaEmision()));
        invoice.put("invoice_type_code", "FACTURA_VENTA");
        invoice.put("payment_means", "DEBIT_AHORRO");
        invoice.put("payment_means_type", "CREDITO");
        invoice.put("customer", customer(documento.adquirente()));
        invoice.put("items", documento.lineas().stream().map(this::item).toList());
        invoice.put("numbering", numbering(documento, current));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("actions", Map.of("send_dian", documento.enviarDian(), "send_email", documento.enviarEmail()));
        request.put("invoice", invoice);
        return request;
    }

    private Map<String, Object> numbering(DocumentoElectronico documento, DataicoProperties current) {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfHasText(result, "prefix", documento.prefijo() == null ? current.prefix() : documento.prefijo());
        putIfHasText(result, "resolution_number", documento.ajustes() == null
                ? current.resolutionNumber() : documento.ajustes().resolucion());
        result.put("flexible", documento.ajustes() == null ? current.flexible() : documento.ajustes().flexible());
        return result;
    }

    private Map<String, Object> customer(AdquirenteElectronico customer) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean natural = esPersonaNatural(customer.tipoPersona());
        result.put("party_type", natural ? "PERSONA_NATURAL" : "PERSONA_JURIDICA");
        result.put("party_identification_type", customer.tipoIdentificacion());
        result.put("party_identification", customer.identificacion());
        result.put("tax_level_code", "SIMPLIFICADO");
        result.put("regimen", "SIMPLE");
        putIfHasText(result, "check_digit", customer.digitoVerificacion());
        if (natural) {
            putIfHasText(result, "first_name", customer.primerNombre());
            putIfHasText(result, "other_names", customer.segundoNombre());
            putIfHasText(result, "family_name", customer.primerApellido());
            putIfHasText(result, "second_last_name", customer.segundoApellido());
        } else {
            putIfHasText(result, "company_name", customer.razonSocial());
        }
        putIfHasText(result, "email", customer.email());
        putIfHasText(result, "phone", customer.telefono());
        if (customer.direccion() != null) {
            putIfHasText(result, "address_line", customer.direccion().linea1());
            putIfHasText(result, "city", customer.direccion().ciudadCodigoDane());
            putIfHasText(result, "department", customer.direccion().departamentoCodigoDane());
            putIfHasText(result, "country_code", customer.direccion().paisCodigo());
        }
        return result;
    }

    private Map<String, Object> item(LineaDocumentoElectronico line) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sku", line.codigo());
        result.put("description", line.descripcion());
        result.put("quantity", line.cantidad());
        result.put("measuring_unit", line.unidadMedida() == null ? "94" : line.unidadMedida());
        result.put("price", line.precioUnitario());
        return result;
    }

    private void validar(DocumentoElectronico documento) {
        if (documento == null || documento.numero() == null || documento.numero().isBlank() || documento.fechaEmision() == null
                || documento.adquirente() == null || documento.lineas().isEmpty()) {
            throw new IllegalArgumentException("La factura requiere numero, fecha, adquirente y al menos una linea.");
        }
    }

    private void putIfHasText(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private boolean esPersonaNatural(String tipoPersona) {
        return "NATURAL".equalsIgnoreCase(tipoPersona) || "PERSON".equalsIgnoreCase(tipoPersona);
    }
}
