package com.posdesktop.pos.ventas.service;

import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import com.posdesktop.pos.facturacion.electronica.config.DataicoConfiguracionService;
import com.posdesktop.pos.facturacion.electronica.domain.AjustesEmision;
import com.posdesktop.pos.facturacion.electronica.domain.AdquirenteElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.DireccionFiscal;
import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.LineaDocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.facturacion.electronica.domain.TipoDocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoApiException;
import com.posdesktop.pos.modelo.relacional.ClienteElectronico;
import com.posdesktop.pos.modelo.relacional.DataicoConsecutivo;
import com.posdesktop.pos.modelo.relacional.DetalleVenta;
import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.ClienteElectronicoRepositorio;
import com.posdesktop.pos.repositorio.relacional.DataicoConsecutivoRepositorio;
import com.posdesktop.pos.repositorio.relacional.FacturaElectronicaRepositorio;
import com.posdesktop.pos.repositorio.relacional.VentaRepositorio;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import com.posdesktop.pos.ventas.api.dto.ResultadoFacturaElectronicaVentaResponse;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emite una venta ya persistida; un fallo externo no revierte el comprobante local. */
@Service
public class FacturacionVentaService {
    private static final Logger log = LoggerFactory.getLogger(FacturacionVentaService.class);
    private static final Pattern SIGUIENTE_NUMERO_DATAICO = Pattern.compile("(?i)siguiente n(?:ú|u)mero ['‘]([^'’]+)['’]");
    private final VentaRepositorio ventaRepositorio;
    private final ClienteElectronicoRepositorio clienteRepositorio;
    private final FacturacionElectronicaService facturacionElectronicaService;
    private final DataicoConfiguracionService configuracion;
    private final FacturaElectronicaRepositorio facturaElectronicaRepositorio;
    private final DataicoConsecutivoRepositorio dataicoConsecutivoRepositorio;

    public FacturacionVentaService(
            VentaRepositorio ventaRepositorio,
            ClienteElectronicoRepositorio clienteRepositorio,
            FacturacionElectronicaService facturacionElectronicaService,
            DataicoConfiguracionService configuracion,
            FacturaElectronicaRepositorio facturaElectronicaRepositorio,
            DataicoConsecutivoRepositorio dataicoConsecutivoRepositorio
    ) {
        this.ventaRepositorio = ventaRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.facturacionElectronicaService = facturacionElectronicaService;
        this.configuracion = configuracion;
        this.facturaElectronicaRepositorio = facturaElectronicaRepositorio;
        this.dataicoConsecutivoRepositorio = dataicoConsecutivoRepositorio;
    }

    @Transactional
    public ResultadoFacturaElectronicaVentaResponse emitir(UUID ventaId, UUID clienteId) {
        Venta venta = ventaRepositorio.findById(ventaId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe una venta con id " + ventaId + "."));
        ClienteElectronico cliente = clienteRepositorio.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un cliente electrónico con id " + clienteId + "."));
        validarResidenciaCliente(cliente);

        DataicoProperties dataicoProperties = configuracion.paraEmision();
        DataicoConsecutivo consecutivo = consecutivoDataico(dataicoProperties);
        dataicoProperties.validarConsecutivo(consecutivo.getSiguienteNumero());
        String numero = numeroConsecutivo(consecutivo.getSiguienteNumero());
        log.info("Intentando emitir venta local {} como factura electrónica {} en Dataico para cliente {}.",
                venta.getNumeroVenta(), numeroFacturaCompleto(numero, dataicoProperties), cliente.getIdentificacion());
        EmisionDataico emision = emitirConConsecutivoDataico(venta, cliente, numero, dataicoProperties);
        actualizarConsecutivo(consecutivo, emision.numero());
        guardarFacturaElectronica(venta, cliente, emision.resultado(), emision.numero(), dataicoProperties);
        log.info("Dataico respondió para factura {}: estado={}, identificador={}",
                emision.numero(), emision.resultado().estado(), emision.resultado().identificadorExterno());
        return new ResultadoFacturaElectronicaVentaResponse(
                emision.resultado().identificadorExterno(), emision.resultado().numero(), emision.resultado().estado(), emision.resultado().cufe(),
                emision.resultado().qrCodeUrl(), emision.resultado().mensaje(), emision.resultado().pdfUrl()
        );
    }

    private EmisionDataico emitirConConsecutivoDataico(Venta venta, ClienteElectronico cliente, String numero, DataicoProperties dataicoProperties) {
        try {
            return new EmisionDataico(facturacionElectronicaService.emitir("dataico", documento(venta, cliente, numero, dataicoProperties)), numero);
        } catch (DataicoApiException exception) {
            String siguiente = siguienteNumeroDataico(exception.getMessage());
            String consecutivoSiguiente = siguiente == null ? null : siguiente.replaceAll("\\D", "");
            if (consecutivoSiguiente == null || consecutivoSiguiente.isBlank() || consecutivoSiguiente.equals(numero)) throw exception;
            dataicoProperties.validarConsecutivo(Long.parseLong(consecutivoSiguiente));
            log.info("Dataico indicó que el siguiente consecutivo de su numeración es {}. Reintentando una sola vez.", siguiente);
            return new EmisionDataico(facturacionElectronicaService.emitir("dataico", documento(venta, cliente, consecutivoSiguiente, dataicoProperties)), consecutivoSiguiente);
        }
    }

    private DocumentoElectronico documento(Venta venta, ClienteElectronico cliente, String numeroDataico, DataicoProperties dataicoProperties) {
        return new DocumentoElectronico(
                TipoDocumentoElectronico.FACTURA_VENTA,
                numeroDataico,
                venta.getFechaVenta().toLocalDate(),
                dataicoProperties.prefix(),
                "COP",
                adquirente(cliente),
                venta.getDetalles().stream().map(this::linea).toList(),
                true,
                cliente.getEmail() != null && !cliente.getEmail().isBlank(),
                new AjustesEmision(dataicoProperties.resolutionNumber(), dataicoProperties.environment(), dataicoProperties.flexible())
        );
    }

    private DataicoConsecutivo consecutivoDataico(DataicoProperties dataicoProperties) {
        String prefix = dataicoProperties.prefix() == null ? "" : dataicoProperties.prefix().trim();
        if (prefix.isBlank()) throw new IllegalArgumentException("Configura el prefijo de facturación de Dataico antes de emitir.");
        return dataicoConsecutivoRepositorio.findByPrefijoForUpdate(prefix)
                .orElseGet(() -> dataicoConsecutivoRepositorio.saveAndFlush(
                        new DataicoConsecutivo(prefix, numeroInicial(dataicoProperties))));
    }

    private long numeroInicial(DataicoProperties dataicoProperties) {
        String configured = dataicoProperties.initialNumber() == null ? "" : dataicoProperties.initialNumber().replaceAll("\\D", "");
        return configured.isBlank() ? 1L : Long.parseLong(configured);
    }

    private String numeroConsecutivo(long consecutivo) {
        return Long.toString(consecutivo);
    }

    private AdquirenteElectronico adquirente(ClienteElectronico cliente) {
        String nombre = cliente.getRazonSocial();
        if ("NATURAL".equalsIgnoreCase(cliente.getTipoPersona())) {
            nombre = String.join(" ", java.util.stream.Stream.of(
                    cliente.getPrimerNombre(), cliente.getSegundoNombre(),
                    cliente.getPrimerApellido(), cliente.getSegundoApellido()
            ).filter(valor -> valor != null && !valor.isBlank()).toList());
        }
        return new AdquirenteElectronico(
                cliente.getTipoPersona(), cliente.getTipoIdentificacion(), cliente.getIdentificacion(),
                cliente.getDigitoVerificacion(), nombre, cliente.getPrimerNombre(), cliente.getSegundoNombre(),
                cliente.getPrimerApellido(), cliente.getSegundoApellido(), cliente.getEmail(), cliente.getTelefono(),
                new DireccionFiscal(cliente.getDireccion(), cliente.getCiudadCodigoDane(),
                        cliente.getDepartamentoCodigoDane(), cliente.getPaisCodigo())
        );
    }

    private void validarResidenciaCliente(ClienteElectronico cliente) {
        if (cliente.getDireccion() == null || cliente.getDireccion().isBlank()
                || cliente.getPaisCodigo() == null || !cliente.getPaisCodigo().matches("(?i)[A-Z]{2}")
                || cliente.getDepartamentoCodigoDane() == null || cliente.getDepartamentoCodigoDane().isBlank()
                || cliente.getCiudadCodigoDane() == null || cliente.getCiudadCodigoDane().isBlank()) {
            throw new IllegalArgumentException("El cliente debe tener dirección, país, departamento y ciudad de residencia antes de facturar electrónicamente.");
        }
        if ("CO".equalsIgnoreCase(cliente.getPaisCodigo())
                && (!cliente.getDepartamentoCodigoDane().matches("\\d{2}") || !cliente.getCiudadCodigoDane().matches("\\d{3}"))) {
            throw new IllegalArgumentException("Para Colombia, registra el departamento DANE de 2 dígitos y la ciudad DANE de 3 dígitos del cliente.");
        }
    }

    private LineaDocumentoElectronico linea(DetalleVenta detalle) {
        String articulo = detalle.getDescripcion();
        return new LineaDocumentoElectronico(
                articulo, articulo, detalle.getCantidad(), "94", detalle.getPrecioUnitario(), BigDecimal.ZERO
        );
    }

    private void guardarFacturaElectronica(Venta venta, ClienteElectronico cliente, ResultadoEmisionDocumento resultado, String numeroEnviado,
            DataicoProperties dataicoProperties) {
        FacturaElectronica factura = new FacturaElectronica();
        factura.setVentaId(venta.getId());
        factura.setFechaEmision(venta.getFechaVenta());
        factura.setNumeracion(dataicoProperties.prefix());
        factura.setNumeroFactura(resultado.numero() == null || resultado.numero().isBlank()
                ? numeroFacturaCompleto(numeroEnviado, dataicoProperties) : numeroFacturaCompleto(resultado.numero(), dataicoProperties));
        factura.setClienteNombre(nombreCliente(cliente));
        factura.setClienteIdentificacion(cliente.getIdentificacion());
        factura.setClienteEmail(cliente.getEmail());
        factura.setImpuestoTotal(venta.getImpuestoTotal());
        factura.setRetencionTotal(BigDecimal.ZERO);
        factura.setTotal(venta.getTotal());
        factura.setEstadoDian(textoO(resultado.estado(), "ENVIADA"));
        factura.setEstadoCliente("REGISTRADO");
        boolean envioCorreo = cliente.getEmail() != null && !cliente.getEmail().isBlank();
        String estadoCorreo = estadoCorreo(resultado.estadoCorreo(), envioCorreo);
        factura.setEstadoCorreo(estadoCorreo);
        factura.setMensajeDian(resultado.mensaje());
        factura.setMensajeCorreo(switch (estadoCorreo) {
            case "ENVIADO" -> "Dataico confirmó el envío del correo.";
            case "ERROR" -> "Dataico reportó un error al enviar el correo.";
            case "NO_ENVIADO" -> "Dataico aún no ha enviado el correo.";
            case "NO_SOLICITADO" -> "No se solicitó envío por correo.";
            default -> "El envío del correo está pendiente de confirmación.";
        });
        factura.setPdfUrl(resultado.pdfUrl());
        factura.setIdentificadorDataico(resultado.identificadorExterno());
        facturaElectronicaRepositorio.saveAndFlush(factura);
    }

    private String nombreCliente(ClienteElectronico cliente) {
        if (!"NATURAL".equalsIgnoreCase(cliente.getTipoPersona())) return cliente.getRazonSocial();
        return String.join(" ", java.util.stream.Stream.of(cliente.getPrimerNombre(), cliente.getSegundoNombre(),
                cliente.getPrimerApellido(), cliente.getSegundoApellido())
                .filter(valor -> valor != null && !valor.isBlank()).toList());
    }

    private String estadoCorreo(String estadoDataico, boolean solicitado) {
        String estado = estadoDataico == null ? "" : estadoDataico.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (estado) {
            case "CORREO_ENVIADO", "ENVIADO" -> "ENVIADO";
            case "CORREO_FALLO", "ERROR" -> "ERROR";
            case "CORREO_PENDIENTE", "PENDIENTE" -> "PENDIENTE";
            case "CORREO_NO_ENVIADO", "NO_ENVIADO" -> solicitado ? "NO_ENVIADO" : "NO_SOLICITADO";
            default -> solicitado ? "PENDIENTE" : "NO_SOLICITADO";
        };
    }

    private String textoO(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String siguienteNumeroDataico(String message) {
        if (message == null) return null;
        Matcher matcher = SIGUIENTE_NUMERO_DATAICO.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private void actualizarConsecutivo(DataicoConsecutivo consecutivo, String numeroEmitido) {
        String digitos = numeroEmitido == null ? "" : numeroEmitido.replaceAll("\\D", "");
        long siguiente = digitos.isBlank() ? consecutivo.getSiguienteNumero() + 1 : Long.parseLong(digitos) + 1;
        consecutivo.setSiguienteNumero(siguiente);
        dataicoConsecutivoRepositorio.save(consecutivo);
    }

    private String numeroFacturaCompleto(String numero, DataicoProperties dataicoProperties) {
        String prefix = dataicoProperties.prefix() == null ? "" : dataicoProperties.prefix().trim();
        return numero != null && numero.startsWith(prefix) ? numero : prefix + numero;
    }

    private record EmisionDataico(ResultadoEmisionDocumento resultado, String numero) { }
}
