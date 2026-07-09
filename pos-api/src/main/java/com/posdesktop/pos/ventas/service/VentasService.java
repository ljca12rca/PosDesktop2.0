package com.posdesktop.pos.ventas.service;

import com.posdesktop.pos.cierres.service.CierreDiarioAutoSyncService;
import com.posdesktop.pos.modelo.relacional.CierreDiario;
import com.posdesktop.pos.modelo.enumeraciones.EstadoCierreDiario;
import com.posdesktop.pos.modelo.relacional.DetalleVenta;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.CierreDiarioRepositorio;
import com.posdesktop.pos.repositorio.relacional.VentaRepositorio;
import com.posdesktop.pos.ventas.api.dto.DetalleVentaResponse;
import com.posdesktop.pos.ventas.api.dto.MovimientoVentaResponse;
import com.posdesktop.pos.ventas.api.dto.RegistrarDetalleVentaRequest;
import com.posdesktop.pos.ventas.api.dto.RegistrarVentaManualRequest;
import com.posdesktop.pos.ventas.api.dto.VentaRegistradaResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VentasService {

    private static final DateTimeFormatter NUMERO_DIA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final VentaRepositorio ventaRepositorio;
    private final CierreDiarioRepositorio cierreDiarioRepositorio;
    private final CierreDiarioAutoSyncService cierreDiarioAutoSyncService;

    public VentasService(
            VentaRepositorio ventaRepositorio,
            CierreDiarioRepositorio cierreDiarioRepositorio,
            CierreDiarioAutoSyncService cierreDiarioAutoSyncService
    ) {
        this.ventaRepositorio = ventaRepositorio;
        this.cierreDiarioRepositorio = cierreDiarioRepositorio;
        this.cierreDiarioAutoSyncService = cierreDiarioAutoSyncService;
    }

    @Transactional
    public VentaRegistradaResponse registrarVentaManual(RegistrarVentaManualRequest request) {
        LocalDate fechaOperacion = LocalDate.now();
        CierreDiario cierreDelDia = validarPermisoVentaMismoDia(fechaOperacion);

        Venta venta = new Venta();
        venta.setNumeroVenta(generarNumeroVenta(fechaOperacion));
        venta.setObservacion(request.observacion());

        int orden = 1;
        for (RegistrarDetalleVentaRequest detalleRequest : request.detalles()) {
            DetalleVenta detalle = DetalleVenta.crearDetalleManual(
                    orden++,
                    detalleRequest.descripcion(),
                    normalizarCantidad(detalleRequest.cantidad()),
                    normalizarDinero(detalleRequest.valorUnitario())
            );
            venta.agregarDetalle(detalle);
        }

        venta.setMontoManualInformado(venta.getTotal());
        BigDecimal montoRecibido = resolverMontoRecibido(request.montoRecibido(), venta.getTotal());
        venta.setMontoRecibido(montoRecibido);
        validarMontoRecibidoContraTotal(venta.getMontoRecibido(), venta.getTotal());
        venta.setCambioEntregado(calcularCambioEntregado(venta.getMontoRecibido(), venta.getTotal()));
        if (debeSincronizarCierreGuardado(fechaOperacion, cierreDelDia)) {
            venta.setEstado(com.posdesktop.pos.modelo.enumeraciones.EstadoVenta.CERRADA);
            venta.setCierreDiario(cierreDelDia);
        }

        Venta ventaGuardada = ventaRepositorio.saveAndFlush(venta);
        if (debeSincronizarCierreGuardado(fechaOperacion, cierreDelDia)) {
            cierreDiarioAutoSyncService.sincronizarSiCierreDelDiaYaExiste(fechaOperacion);
        }
        return mapearVenta(ventaGuardada);
    }

    @Transactional(readOnly = true)
    public List<MovimientoVentaResponse> listarMovimientos(LocalDate fechaInicial, LocalDate fechaFinal) {
        LocalDate fechaInicioConsulta = fechaInicial == null ? LocalDate.now() : fechaInicial;
        LocalDate fechaFinConsulta = fechaFinal == null ? fechaInicioConsulta : fechaFinal;
        validarRango(fechaInicioConsulta, fechaFinConsulta);

        return ventaRepositorio.findByFechaVentaBetweenOrderByFechaVentaDesc(
                        fechaInicioConsulta.atStartOfDay(),
                        fechaFinConsulta.atTime(LocalTime.MAX)
                )
                .stream()
                .map(this::mapearMovimiento)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Venta> ventasDelDia(LocalDate fechaOperacion) {
        LocalDate fecha = fechaOperacion == null ? LocalDate.now() : fechaOperacion;
        return ventaRepositorio.findByFechaVentaBetween(fecha.atStartOfDay(), fecha.atTime(LocalTime.MAX));
    }

    private String generarNumeroVenta(LocalDate fecha) {
        LocalDate fechaGeneracion = fecha == null ? LocalDate.now() : fecha;
        LocalDateTime inicio = fechaGeneracion.atStartOfDay();
        LocalDateTime fin = fechaGeneracion.atTime(LocalTime.MAX);
        long consecutivo = ventaRepositorio.countByFechaVentaBetween(inicio, fin) + 1;
        return "VTA-" + fechaGeneracion.format(NUMERO_DIA) + "-" + String.format("%04d", consecutivo);
    }

    private void validarRango(LocalDate fechaInicial, LocalDate fechaFinal) {
        if (fechaFinal.isBefore(fechaInicial)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        }
    }

    private CierreDiario validarPermisoVentaMismoDia(LocalDate fechaOperacion) {
        CierreDiario cierre = cierreDiarioRepositorio.findByFechaOperacion(fechaOperacion).orElse(null);
        if (cierre == null || cierre.getEstado() != EstadoCierreDiario.CERRADO) {
            return cierre;
        }

        LocalDate hoy = LocalDate.now();
        if (!fechaOperacion.equals(hoy) || !cierre.getFechaOperacion().equals(hoy)) {
            throw new IllegalArgumentException(
                    "No es posible registrar ventas porque el cierre guardado no corresponde al dia actual."
            );
        }
        return cierre;
    }

    private boolean debeSincronizarCierreGuardado(LocalDate fechaOperacion, CierreDiario cierreDelDia) {
        LocalDate hoy = LocalDate.now();
        return cierreDelDia != null
                && cierreDelDia.getEstado() == EstadoCierreDiario.CERRADO
                && fechaOperacion.equals(hoy)
                && cierreDelDia.getFechaOperacion().equals(hoy);
    }

    private void validarMontoRecibidoContraTotal(BigDecimal montoRecibido, BigDecimal totalVenta) {
        if (montoRecibido != null
                && montoRecibido.signum() > 0
                && totalVenta != null
                && montoRecibido.compareTo(totalVenta) < 0) {
            throw new IllegalArgumentException("El valor recibido no puede ser menor al valor total de la venta.");
        }
    }

    private BigDecimal calcularCambioEntregado(BigDecimal montoRecibido, BigDecimal totalVenta) {
        BigDecimal recibido = normalizarDinero(montoRecibido);
        if (recibido.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return recibido.subtract(normalizarDinero(totalVenta)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolverMontoRecibido(BigDecimal montoRecibido, BigDecimal totalVenta) {
        BigDecimal recibidoNormalizado = normalizarDinero(montoRecibido);
        if (recibidoNormalizado.signum() > 0) {
            return recibidoNormalizado;
        }
        return normalizarDinero(totalVenta);
    }

    private VentaRegistradaResponse mapearVenta(Venta venta) {
        List<DetalleVentaResponse> detalles = venta.getDetalles().stream()
                .map(this::mapearDetalle)
                .toList();

        return new VentaRegistradaResponse(
                venta.getId(),
                venta.getNumeroVenta(),
                venta.getFechaVenta(),
                venta.getSubtotal(),
                venta.getTotal(),
                venta.getMontoRecibido(),
                venta.getCambioEntregado(),
                detalles.size(),
                detalles
        );
    }

    private DetalleVentaResponse mapearDetalle(DetalleVenta detalle) {
        return new DetalleVentaResponse(
                detalle.getId(),
                detalle.getOrden(),
                detalle.getDescripcion(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal(),
                detalle.getTotal()
        );
    }

    private MovimientoVentaResponse mapearMovimiento(Venta venta) {
        return new MovimientoVentaResponse(
                venta.getId(),
                venta.getNumeroVenta(),
                venta.getOrigen().name(),
                venta.getTotal(),
                venta.getMontoRecibido(),
                venta.getCambioEntregado(),
                venta.getFechaVenta()
        );
    }

    private BigDecimal normalizarDinero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizarCantidad(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(3, RoundingMode.HALF_UP);
    }
}
