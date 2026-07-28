package com.posdesktop.pos.facturas.service;

import com.posdesktop.pos.facturas.api.dto.ActualizarFacturaProveedorRequest;
import com.posdesktop.pos.facturas.api.dto.DocumentoSoporteResponse;
import com.posdesktop.pos.facturas.api.dto.FacturaProveedorDetalleResponse;
import com.posdesktop.pos.facturas.api.dto.FacturaProveedorListadoResponse;
import com.posdesktop.pos.facturas.api.dto.PagoFacturaResponse;
import com.posdesktop.pos.facturas.api.dto.ProveedorFacturaResponse;
import com.posdesktop.pos.facturas.api.dto.RegistrarFacturaProveedorRequest;
import com.posdesktop.pos.facturas.api.dto.RegistrarPagoFacturaRequest;
import com.posdesktop.pos.modelo.documental.DocumentoSoporte;
import com.posdesktop.pos.modelo.enumeraciones.EntidadOrigenDocumento;
import com.posdesktop.pos.modelo.enumeraciones.EstadoFacturaProveedor;
import com.posdesktop.pos.modelo.enumeraciones.MetodoPagoFactura;
import com.posdesktop.pos.modelo.relacional.FacturaProveedor;
import com.posdesktop.pos.modelo.relacional.PagoFactura;
import com.posdesktop.pos.modelo.relacional.Proveedor;
import com.posdesktop.pos.repositorio.documental.DocumentoSoporteRepositorio;
import com.posdesktop.pos.repositorio.relacional.FacturaProveedorRepositorio;
import com.posdesktop.pos.repositorio.relacional.PagoFacturaRepositorio;
import com.posdesktop.pos.repositorio.relacional.ProveedorRepositorio;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FacturasProveedorService {

    private final FacturaProveedorRepositorio facturaProveedorRepositorio;
    private final PagoFacturaRepositorio pagoFacturaRepositorio;
    private final ProveedorRepositorio proveedorRepositorio;
    private final DocumentoSoporteRepositorio documentoSoporteRepositorio;
    private final DocumentoSoporteStorageService documentoSoporteStorageService;

    public FacturasProveedorService(
            FacturaProveedorRepositorio facturaProveedorRepositorio,
            PagoFacturaRepositorio pagoFacturaRepositorio,
            ProveedorRepositorio proveedorRepositorio,
            DocumentoSoporteRepositorio documentoSoporteRepositorio,
            DocumentoSoporteStorageService documentoSoporteStorageService
    ) {
        this.facturaProveedorRepositorio = facturaProveedorRepositorio;
        this.pagoFacturaRepositorio = pagoFacturaRepositorio;
        this.proveedorRepositorio = proveedorRepositorio;
        this.documentoSoporteRepositorio = documentoSoporteRepositorio;
        this.documentoSoporteStorageService = documentoSoporteStorageService;
    }

    @Transactional(readOnly = true)
    public List<FacturaProveedorListadoResponse> listarFacturas(UUID proveedorId, EstadoFacturaProveedor estado) {
        List<FacturaProveedor> facturas = consultarFacturas(proveedorId);
        return facturas.stream()
                .peek(FacturaProveedor::recalcularSaldo)
                .filter(factura -> estado == null || factura.getEstado() == estado)
                .map(this::mapearListado)
                .toList();
    }

    @Transactional(readOnly = true)
    public FacturaProveedorDetalleResponse consultarFactura(UUID facturaId) {
        FacturaProveedor factura = obtenerFactura(facturaId);
        factura.recalcularSaldo();

        List<PagoFactura> pagos = pagoFacturaRepositorio.findByFacturaProveedorIdOrderByFechaPagoAscCreadoEnAsc(facturaId);
        List<DocumentoSoporte> soportesFactura = documentoSoporteRepositorio.findByEntidadOrigenAndEntidadOrigenId(
                EntidadOrigenDocumento.FACTURA_PROVEEDOR,
                facturaId.toString()
        );

        List<String> pagosIds = pagos.stream()
                .map(PagoFactura::getId)
                .map(UUID::toString)
                .toList();
        Map<String, List<DocumentoSoporte>> soportesPago = pagosIds.isEmpty()
                ? Map.of()
                : documentoSoporteRepositorio.findByEntidadOrigenAndEntidadOrigenIdIn(EntidadOrigenDocumento.PAGO_FACTURA, pagosIds)
                .stream()
                .collect(Collectors.groupingBy(DocumentoSoporte::getEntidadOrigenId));

        return mapearDetalle(factura, pagos, soportesFactura, soportesPago);
    }

    @Transactional
    public FacturaProveedorDetalleResponse registrarFactura(
            RegistrarFacturaProveedorRequest request,
            List<MultipartFile> imagenes
    ) {
        UUID proveedorId = convertirUuid(request.proveedorId(), "El proveedor enviado no es valido.");
        Proveedor proveedor = proveedorRepositorio.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un proveedor con id " + request.proveedorId() + "."));

        String numeroFactura = limpiarRequerido(request.numeroFactura());
        facturaProveedorRepositorio.findByProveedorIdAndNumeroFacturaIgnoreCase(proveedorId, numeroFactura)
                .ifPresent(factura -> {
                    throw new IllegalArgumentException(
                            "Ya existe la factura " + numeroFactura + " para el proveedor " + proveedor.getNombre() + "."
                    );
                });

        BigDecimal valorTotal = normalizarDinero(request.valorTotal());
        BigDecimal saldoInicial = normalizarDinero(request.saldoInicial());
        validarSaldoInicial(valorTotal, saldoInicial);

        FacturaProveedor factura = new FacturaProveedor();
        factura.setProveedor(proveedor);
        factura.setNumeroFactura(numeroFactura);
        factura.setFechaEmision(request.fechaEmision());
        factura.setFechaVencimiento(request.fechaVencimiento());
        factura.setObservacion(limpiarOpcional(request.observacion()));
        factura.setMontoTotal(valorTotal);

        BigDecimal montoPagadoInicial = valorTotal.subtract(saldoInicial).setScale(2, RoundingMode.HALF_UP);
        if (montoPagadoInicial.signum() > 0) {
            factura.registrarPago(crearPagoInicial(request, montoPagadoInicial));
        } else {
            factura.recalcularSaldo();
        }

        FacturaProveedor guardada = facturaProveedorRepositorio.saveAndFlush(factura);
        documentoSoporteStorageService.guardarSoportesFactura(guardada, imagenes);
        return consultarFactura(guardada.getId());
    }

    @Transactional
    public FacturaProveedorDetalleResponse actualizarFactura(
            UUID facturaId,
            ActualizarFacturaProveedorRequest request
    ) {
        FacturaProveedor factura = obtenerFactura(facturaId);
        factura.recalcularSaldo();

        String numeroFactura = limpiarRequerido(request.numeroFactura());
        facturaProveedorRepositorio.findByProveedorIdAndNumeroFacturaIgnoreCase(factura.getProveedor().getId(), numeroFactura)
                .ifPresent(existente -> {
                    if (!existente.getId().equals(factura.getId())) {
                        throw new IllegalArgumentException(
                                "Ya existe la factura " + numeroFactura + " para el proveedor "
                                        + factura.getProveedor().getNombre() + "."
                        );
                    }
                });

        BigDecimal valorTotal = normalizarDinero(request.valorTotal());
        if (valorTotal.compareTo(factura.getMontoPagado()) < 0) {
            throw new IllegalArgumentException(
                    "El valor total no puede ser menor a lo ya abonado en la factura ("
                            + factura.getMontoPagado().setScale(2, RoundingMode.HALF_UP).toPlainString() + ")."
            );
        }

        factura.setNumeroFactura(numeroFactura);
        factura.setFechaEmision(request.fechaEmision());
        factura.setFechaVencimiento(request.fechaVencimiento());
        factura.setObservacion(limpiarOpcional(request.observacion()));
        factura.setMontoTotal(valorTotal);

        FacturaProveedor guardada = facturaProveedorRepositorio.saveAndFlush(factura);
        return consultarFactura(guardada.getId());
    }

    @Transactional
    public FacturaProveedorDetalleResponse registrarAbono(
            UUID facturaId,
            RegistrarPagoFacturaRequest request,
            List<MultipartFile> soportes
    ) {
        FacturaProveedor factura = obtenerFactura(facturaId);
        factura.recalcularSaldo();

        BigDecimal valorAbono = normalizarDinero(request.valorAbono());
        validarNuevoAbono(factura, valorAbono);

        PagoFactura pago = new PagoFactura();
        pago.setFechaPago(request.fechaPago() == null ? LocalDate.now() : request.fechaPago());
        pago.setMontoPago(valorAbono);
        pago.setMetodoPago(request.metodoPago() == null ? MetodoPagoFactura.TRANSFERENCIA : request.metodoPago());
        pago.setReferenciaPago(limpiarOpcional(request.referenciaPago()));
        pago.setObservacion(limpiarOpcional(request.observacion()));

        factura.registrarPago(pago);
        FacturaProveedor guardada = facturaProveedorRepositorio.saveAndFlush(factura);
        PagoFactura pagoGuardado = guardada.getPagos().stream()
                .max(Comparator.comparing(PagoFactura::getCreadoEn))
                .orElseThrow(() -> new IllegalStateException("No fue posible recuperar el abono recien creado."));

        documentoSoporteStorageService.guardarSoportesPago(guardada, pagoGuardado, soportes);
        return consultarFactura(guardada.getId());
    }

    private List<FacturaProveedor> consultarFacturas(UUID proveedorId) {
        if (proveedorId != null) {
            return facturaProveedorRepositorio.findByProveedorIdOrderByFechaEmisionDescNumeroFacturaDesc(proveedorId);
        }
        return facturaProveedorRepositorio.findAllByOrderByFechaEmisionDescNumeroFacturaDesc();
    }

    private FacturaProveedor obtenerFactura(UUID facturaId) {
        return facturaProveedorRepositorio.findById(facturaId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe una factura proveedor con id " + facturaId + "."));
    }

    private PagoFactura crearPagoInicial(RegistrarFacturaProveedorRequest request, BigDecimal montoPagadoInicial) {
        PagoFactura pago = new PagoFactura();
        pago.setFechaPago(request.fechaEmision());
        pago.setMontoPago(montoPagadoInicial);
        pago.setMetodoPago(MetodoPagoFactura.OTRO);
        pago.setReferenciaPago("PAGO-INICIAL");
        pago.setObservacion("Pago inicial informado al crear la factura.");
        return pago;
    }

    private void validarSaldoInicial(BigDecimal valorTotal, BigDecimal saldoInicial) {
        if (saldoInicial.compareTo(valorTotal) > 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser mayor al valor total de la factura.");
        }
    }

    private void validarNuevoAbono(FacturaProveedor factura, BigDecimal valorAbono) {
        if (factura.getEstado() == EstadoFacturaProveedor.CANCELADA) {
            throw new IllegalArgumentException("No es posible registrar abonos sobre una factura cancelada.");
        }
        if (factura.getSaldoPendiente().signum() <= 0) {
            throw new IllegalArgumentException("La factura seleccionada ya no tiene saldo pendiente.");
        }
        if (valorAbono.compareTo(factura.getSaldoPendiente()) > 0) {
            throw new IllegalArgumentException("El abono no puede superar el saldo pendiente de la factura.");
        }
    }

    private FacturaProveedorListadoResponse mapearListado(FacturaProveedor factura) {
        return new FacturaProveedorListadoResponse(
                factura.getId().toString(),
                factura.getProveedor().getId().toString(),
                factura.getProveedor().getNombre(),
                factura.getProveedor().getNit(),
                factura.getNumeroFactura(),
                factura.getFechaEmision(),
                factura.getFechaVencimiento(),
                factura.getEstado().name(),
                factura.getMontoTotal(),
                factura.getMontoPagado(),
                factura.getSaldoPendiente(),
                factura.getObservacion()
        );
    }

    private FacturaProveedorDetalleResponse mapearDetalle(
            FacturaProveedor factura,
            List<PagoFactura> pagos,
            List<DocumentoSoporte> soportesFactura,
            Map<String, List<DocumentoSoporte>> soportesPago
    ) {
        return new FacturaProveedorDetalleResponse(
                factura.getId().toString(),
                new ProveedorFacturaResponse(
                        factura.getProveedor().getId().toString(),
                        factura.getProveedor().getNit(),
                        factura.getProveedor().getNombre(),
                        factura.getProveedor().getTelefono(),
                        factura.getProveedor().getEmail()
                ),
                factura.getNumeroFactura(),
                factura.getFechaEmision(),
                factura.getFechaVencimiento(),
                factura.getEstado().name(),
                factura.getMontoTotal(),
                factura.getMontoPagado(),
                factura.getSaldoPendiente(),
                factura.getObservacion(),
                soportesFactura.stream().map(this::mapearDocumento).toList(),
                mapearPagos(factura, pagos, soportesPago)
        );
    }

    private List<PagoFacturaResponse> mapearPagos(
            FacturaProveedor factura,
            List<PagoFactura> pagos,
            Map<String, List<DocumentoSoporte>> soportesPago
    ) {
        BigDecimal saldoRestante = factura.getMontoTotal();
        List<PagoFacturaResponse> respuesta = new java.util.ArrayList<>();
        for (PagoFactura pago : pagos) {
            saldoRestante = saldoRestante.subtract(normalizarDinero(pago.getMontoPago())).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            List<DocumentoSoporteResponse> soportes = soportesPago.getOrDefault(pago.getId().toString(), List.of()).stream()
                    .map(this::mapearDocumento)
                    .toList();
            respuesta.add(new PagoFacturaResponse(
                    pago.getId().toString(),
                    pago.getFechaPago(),
                    pago.getMontoPago(),
                    pago.getMetodoPago().name(),
                    pago.getReferenciaPago(),
                    pago.getObservacion(),
                    saldoRestante,
                    soportes
            ));
        }
        return respuesta;
    }

    private DocumentoSoporteResponse mapearDocumento(DocumentoSoporte documento) {
        return new DocumentoSoporteResponse(
                documento.getId(),
                documento.getEntidadOrigen() == null ? null : documento.getEntidadOrigen().name(),
                documento.getEntidadOrigenId(),
                documento.getTipoDocumento() == null ? null : documento.getTipoDocumento().name(),
                documento.getNombreArchivo(),
                documento.getContentType(),
                documento.getTamanioBytes(),
                documento.getRutaArchivo(),
                documento.getRutaRelativa(),
                documento.getCarpetas(),
                documento.getChecksum(),
                documento.getObservacion(),
                documento.getCargadoEn()
        );
    }

    private UUID convertirUuid(String valor, String mensaje) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(mensaje, exception);
        }
    }

    private BigDecimal normalizarDinero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String limpiarRequerido(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private String limpiarOpcional(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
