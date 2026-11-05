package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import com.posdesktop.pos.facturacion.electronica.domain.*;
import com.posdesktop.pos.modelo.relacional.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Snapshot v1 de la solicitud legada y de sus importes comerciales; sin tokens ni claves. */
public record DataicoSalePayload(UUID saleId, UUID customerId, String localNumber, LocalDateTime date,
        String customerName, String email, BigDecimal tax, BigDecimal discount, BigDecimal total,
        List<CommercialLine> commercialLines, String account, String baseUrl, DocumentoElectronico document, String providerRequestJson) {
    public DataicoSalePayload { commercialLines = List.copyOf(commercialLines); }
    public record CommercialLine(int order, String description, BigDecimal quantity, BigDecimal price,
            BigDecimal discount, BigDecimal tax, BigDecimal total) { }
    public static DataicoSalePayload create(Venta sale, ClienteElectronico customer, String sequence, DataicoProperties config) {
        validateAddress(customer);
        String name = customerName(customer);
        var party = new AdquirenteElectronico(customer.getTipoPersona(), customer.getTipoIdentificacion(), customer.getIdentificacion(),
                customer.getDigitoVerificacion(), name, customer.getPrimerNombre(), customer.getSegundoNombre(), customer.getPrimerApellido(),
                customer.getSegundoApellido(), customer.getEmail(), customer.getTelefono(),
                new DireccionFiscal(customer.getDireccion(), customer.getCiudadCodigoDane(), customer.getDepartamentoCodigoDane(), customer.getPaisCodigo()));
        var document = new DocumentoElectronico(TipoDocumentoElectronico.FACTURA_VENTA, sequence, sale.getFechaVenta().toLocalDate(), config.prefix(), "COP", party,
                sale.getDetalles().stream().map(d -> new LineaDocumentoElectronico(d.getDescripcion(), d.getDescripcion(), d.getCantidad(), "94", d.getPrecioUnitario(), BigDecimal.ZERO)).toList(),
                true, customer.getEmail() != null && !customer.getEmail().isBlank(), new AjustesEmision(config.resolutionNumber(), config.environment(), config.flexible()));
        var details = sale.getDetalles().stream().map(d -> new CommercialLine(d.getOrden(),d.getDescripcion(),d.getCantidad(),d.getPrecioUnitario(),d.getDescuento(),d.getImpuesto(),d.getTotal())).toList();
        String request=new FiscalJson().write(new com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoInvoiceMapper(config).toRequest(document));
        return new DataicoSalePayload(sale.getId(), customer.getId(), sale.getNumeroVenta(), sale.getFechaVenta(), name, customer.getEmail(),
                sale.getImpuestoTotal(), sale.getDescuentoTotal(), sale.getTotal(), details, config.accountId(), config.baseUrl(), document,request);
    }
    public DataicoSalePayload renumber(String number) {
        var d = document;
        var json=new FiscalJson();
        @SuppressWarnings("unchecked") Map<String,Object> request=json.read(providerRequestJson,Map.class);
        @SuppressWarnings("unchecked") Map<String,Object> invoice=(Map<String,Object>)request.get("invoice");
        invoice.put("number",number);
        return new DataicoSalePayload(saleId,customerId,localNumber,date,customerName,email,tax,discount,total,commercialLines,account,baseUrl,
                new DocumentoElectronico(d.tipo(),number,d.fechaEmision(),d.prefijo(),d.moneda(),d.adquirente(),d.lineas(),d.enviarDian(),d.enviarEmail(),d.ajustes()),json.write(request));
    }
    public String fullNumber() { return document.prefijo() + document.numero(); }
    public static String customerName(ClienteElectronico customer) {
        if (!"NATURAL".equalsIgnoreCase(customer.getTipoPersona())) return customer.getRazonSocial();
        return String.join(" ", java.util.stream.Stream.of(customer.getPrimerNombre(),customer.getSegundoNombre(),customer.getPrimerApellido(),customer.getSegundoApellido())
                .filter(s -> s != null && !s.isBlank()).toList());
    }
    private static void validateAddress(ClienteElectronico customer) {
        if (customer.getDireccion() == null || customer.getDireccion().isBlank() || customer.getPaisCodigo() == null
                || !customer.getPaisCodigo().matches("(?i)[A-Z]{2}") || customer.getDepartamentoCodigoDane() == null
                || customer.getDepartamentoCodigoDane().isBlank() || customer.getCiudadCodigoDane() == null || customer.getCiudadCodigoDane().isBlank())
            throw new IllegalArgumentException("El cliente debe tener direccion, pais, departamento y ciudad antes de facturar electronicamente.");
        if ("CO".equalsIgnoreCase(customer.getPaisCodigo()) && (!customer.getDepartamentoCodigoDane().matches("\\d{2}") || !customer.getCiudadCodigoDane().matches("\\d{3}")))
            throw new IllegalArgumentException("Para Colombia, registra departamento DANE de 2 digitos y ciudad DANE de 3 digitos.");
    }
}
