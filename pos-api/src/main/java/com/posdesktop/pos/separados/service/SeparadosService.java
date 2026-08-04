package com.posdesktop.pos.separados.service;

import com.posdesktop.pos.auth.service.AuthSessionData;
import com.posdesktop.pos.modelo.enumeraciones.EstadoSeparado;
import com.posdesktop.pos.modelo.enumeraciones.MedioPagoVenta;
import com.posdesktop.pos.modelo.relacional.AbonoSeparado;
import com.posdesktop.pos.modelo.relacional.Separado;
import com.posdesktop.pos.modelo.relacional.UsuarioSistema;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.AbonoSeparadoRepositorio;
import com.posdesktop.pos.repositorio.relacional.SeparadoRepositorio;
import com.posdesktop.pos.repositorio.relacional.UsuarioSistemaRepositorio;
import com.posdesktop.pos.separados.api.dto.AbonoSeparadoResponse;
import com.posdesktop.pos.separados.api.dto.RegistrarAbonoSeparadoRequest;
import com.posdesktop.pos.separados.api.dto.RegistrarSeparadoRequest;
import com.posdesktop.pos.separados.api.dto.SeparadoDetalleResponse;
import com.posdesktop.pos.separados.api.dto.SeparadoListadoResponse;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import com.posdesktop.pos.ventas.service.VentasService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeparadosService {

    private final SeparadoRepositorio separadoRepositorio;
    private final AbonoSeparadoRepositorio abonoSeparadoRepositorio;
    private final VentasService ventasService;
    private final SeparadoNumeroService separadoNumeroService;
    private final SeparadoValidationService separadoValidationService;
    private final UsuarioSistemaRepositorio usuarioSistemaRepositorio;

    public SeparadosService(
            SeparadoRepositorio separadoRepositorio,
            AbonoSeparadoRepositorio abonoSeparadoRepositorio,
            VentasService ventasService,
            SeparadoNumeroService separadoNumeroService,
            SeparadoValidationService separadoValidationService,
            UsuarioSistemaRepositorio usuarioSistemaRepositorio
    ) {
        this.separadoRepositorio = separadoRepositorio;
        this.abonoSeparadoRepositorio = abonoSeparadoRepositorio;
        this.ventasService = ventasService;
        this.separadoNumeroService = separadoNumeroService;
        this.separadoValidationService = separadoValidationService;
        this.usuarioSistemaRepositorio = usuarioSistemaRepositorio;
    }

    @Transactional
    public SeparadoDetalleResponse registrarSeparado(RegistrarSeparadoRequest request, AuthSessionData session) {
        BigDecimal valorTotal = normalizarDinero(request.valorTotal());
        BigDecimal abonoInicial = normalizarDinero(request.abonoInicial());
        separadoValidationService.validarRegistro(valorTotal, abonoInicial);
        UsuarioSistema responsableUsuario = resolveResponsibleUser(session);

        LocalDate fechaSeparacion = LocalDate.now();
        Separado separado = new Separado();
        separado.setNumeroSeparado(separadoNumeroService.generarNumero(fechaSeparacion));
        separado.setFechaSeparacion(fechaSeparacion);
        separado.setNombreCliente(limpiarRequerido(request.cliente()));
        separado.setTelefonoCliente(limpiarOpcional(request.telefonoCliente()));
        separado.setDescripcionArticulo(limpiarRequerido(request.descripcionArticulos()));
        separado.setValorTotal(valorTotal);
        separado.setObservacion(limpiarOpcional(request.observacion()));
        separado.setResponsableUsuario(responsableUsuario);

        Venta ventaAbonoInicial = ventasService.registrarVentaSeparado(
                construirDescripcionVenta(separado, true),
                abonoInicial,
                construirObservacionVenta(separado, request.observacion(), true),
                normalizarMedioPago(request.medioPago())
        );

        AbonoSeparado abonoInicialEntity = crearAbono(
                separado,
                ventaAbonoInicial,
                1,
                abonoInicial,
                true,
                request.observacion(),
                responsableUsuario
        );
        separado.agregarAbono(abonoInicialEntity);

        Separado guardado = separadoRepositorio.saveAndFlush(separado);
        return mapearDetalle(guardado, guardado.getAbonos());
    }

    @Transactional(readOnly = true)
    public List<SeparadoListadoResponse> listarSeparados(EstadoSeparado estado, String articulo) {
        String articuloFiltro = limpiarOpcional(articulo);
        List<Separado> separados;
        if (estado != null && articuloFiltro != null) {
            separados = separadoRepositorio.findByEstadoAndDescripcionArticuloContainingIgnoreCaseOrderByFechaSeparacionDescNumeroSeparadoDesc(
                    estado,
                    articuloFiltro
            );
        } else if (estado != null) {
            separados = separadoRepositorio.findByEstadoOrderByFechaSeparacionDescNumeroSeparadoDesc(estado);
        } else if (articuloFiltro != null) {
            separados = separadoRepositorio.findByDescripcionArticuloContainingIgnoreCaseOrderByFechaSeparacionDescNumeroSeparadoDesc(
                    articuloFiltro
            );
        } else {
            separados = separadoRepositorio.findAllByOrderByFechaSeparacionDescNumeroSeparadoDesc();
        }

        return separados.stream()
                .map(this::mapearListado)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeparadoDetalleResponse consultarSeparado(UUID separadoId) {
        Separado separado = obtenerSeparado(separadoId);
        List<AbonoSeparado> abonos = abonoSeparadoRepositorio.findBySeparadoIdOrderByNumeroAbonoAsc(separadoId);
        return mapearDetalle(separado, abonos);
    }

    @Transactional
    public SeparadoDetalleResponse registrarAbono(
            UUID separadoId,
            RegistrarAbonoSeparadoRequest request,
            AuthSessionData session
    ) {
        Separado separado = obtenerSeparado(separadoId);
        BigDecimal valorAbono = normalizarDinero(request.valorAbono());
        separadoValidationService.validarNuevoAbono(separado, valorAbono);
        UsuarioSistema responsableUsuario = resolveResponsibleUser(session);

        int numeroAbono = separado.getAbonos().size() + 1;
        Venta ventaAbono = ventasService.registrarVentaSeparado(
                construirDescripcionVenta(separado, false),
                valorAbono,
                construirObservacionVenta(separado, request.observacion(), false),
                normalizarMedioPago(request.medioPago())
        );

        AbonoSeparado abono = crearAbono(
                separado,
                ventaAbono,
                numeroAbono,
                valorAbono,
                false,
                request.observacion(),
                responsableUsuario
        );
        separado.agregarAbono(abono);

        Separado guardado = separadoRepositorio.saveAndFlush(separado);
        List<AbonoSeparado> abonos = abonoSeparadoRepositorio.findBySeparadoIdOrderByNumeroAbonoAsc(guardado.getId());
        return mapearDetalle(guardado, abonos);
    }

    private Separado obtenerSeparado(UUID separadoId) {
        return separadoRepositorio.findById(separadoId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un separado con id " + separadoId + "."));
    }

    private AbonoSeparado crearAbono(
            Separado separado,
            Venta venta,
            int numeroAbono,
            BigDecimal montoAbono,
            boolean abonoInicial,
            String observacion,
            UsuarioSistema responsableUsuario
    ) {
        AbonoSeparado abono = new AbonoSeparado();
        abono.setSeparado(separado);
        abono.setVenta(venta);
        abono.setNumeroAbono(numeroAbono);
        abono.setFechaAbono(LocalDateTime.now());
        abono.setMontoAbono(montoAbono);
        abono.setAbonoInicial(abonoInicial);
        abono.setObservacion(limpiarOpcional(observacion));
        abono.setResponsableUsuario(responsableUsuario);
        return abono;
    }

    private UsuarioSistema resolveResponsibleUser(AuthSessionData session) {
        if (session == null || session.usuarioId() == null || session.usuarioId().isBlank()) {
            throw new IllegalStateException("No fue posible identificar al usuario responsable de la operacion.");
        }
        try {
            UUID usuarioId = UUID.fromString(session.usuarioId());
            return usuarioSistemaRepositorio.findById(usuarioId)
                    .filter(UsuarioSistema::isActivo)
                    .orElseThrow(() -> new IllegalStateException("El usuario responsable no existe o esta inactivo."));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("La sesion contiene un identificador de usuario invalido.", exception);
        }
    }

    private SeparadoListadoResponse mapearListado(Separado separado) {
        return new SeparadoListadoResponse(
                separado.getId().toString(),
                separado.getNumeroSeparado(),
                separado.getNombreCliente(),
                separado.getDescripcionArticulo(),
                separado.getEstado().name(),
                separado.getValorTotal(),
                separado.getTotalAbonado(),
                separado.getSaldoPendiente(),
                separado.getFechaSeparacion(),
                separado.getResponsableUsuario().getNombreCompleto()
        );
    }

    private SeparadoDetalleResponse mapearDetalle(Separado separado, List<AbonoSeparado> abonos) {
        return new SeparadoDetalleResponse(
                separado.getId().toString(),
                separado.getNumeroSeparado(),
                separado.getNombreCliente(),
                separado.getTelefonoCliente(),
                separado.getDescripcionArticulo(),
                separado.getEstado().name(),
                separado.getValorTotal(),
                separado.getMontoMinimoInicial(),
                separado.getTotalAbonado(),
                separado.getSaldoPendiente(),
                separado.getFechaSeparacion(),
                separado.getFechaEntrega(),
                separado.getObservacion(),
                abonos.stream().map(this::mapearAbono).toList()
        );
    }

    private AbonoSeparadoResponse mapearAbono(AbonoSeparado abono) {
        return new AbonoSeparadoResponse(
                abono.getId().toString(),
                abono.getNumeroAbono(),
                abono.getFechaAbono(),
                abono.getMontoAbono(),
                abono.isAbonoInicial(),
                abono.getVenta() == null ? null : abono.getVenta().getNumeroVenta(),
                abono.getObservacion(),
                abono.getResponsableUsuario().getNombreCompleto(),
                abono.getVenta() == null ? null : normalizarMedioPago(abono.getVenta().getMedioPago()).name()
        );
    }

    private MedioPagoVenta normalizarMedioPago(MedioPagoVenta medioPago) {
        return medioPago == null ? MedioPagoVenta.EFECTIVO : medioPago;
    }

    private String construirDescripcionVenta(Separado separado, boolean abonoInicial) {
        String prefijo = abonoInicial ? "Abono inicial" : "Abono separado";
        return prefijo + " " + separado.getNumeroSeparado() + " - " + separado.getDescripcionArticulo();
    }

    private String construirObservacionVenta(Separado separado, String observacion, boolean abonoInicial) {
        String prefijo = abonoInicial ? "Apertura de separado " : "Abono de separado ";
        String base = prefijo + separado.getNumeroSeparado();
        String observacionLimpia = limpiarOpcional(observacion);
        if (observacionLimpia == null) {
            return base;
        }
        return base + " | " + observacionLimpia;
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
