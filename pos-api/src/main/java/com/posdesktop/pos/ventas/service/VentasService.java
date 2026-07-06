package com.posdesktop.pos.ventas.service;

import com.posdesktop.pos.modelo.relacional.DetalleVenta;
import com.posdesktop.pos.modelo.relacional.Venta;
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

    public VentasService(VentaRepositorio ventaRepositorio) {
        this.ventaRepositorio = ventaRepositorio;
    }

    @Transactional
    public VentaRegistradaResponse registrarVentaManual(RegistrarVentaManualRequest request) {
        Venta venta = new Venta();
        venta.setNumeroVenta(generarNumeroVenta(LocalDate.now()));
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
        venta.setMontoRecibido(normalizarDinero(request.montoRecibido()));
        validarMontoRecibidoContraTotal(venta.getMontoRecibido(), venta.getTotal());
        venta.setCambioEntregado(venta.getMontoRecibido().subtract(venta.getTotal()));

        Venta ventaGuardada = ventaRepositorio.save(venta);
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

    private void validarMontoRecibidoContraTotal(BigDecimal montoRecibido, BigDecimal totalVenta) {
        if (montoRecibido != null
                && montoRecibido.signum() > 0
                && totalVenta != null
                && montoRecibido.compareTo(totalVenta) < 0) {
            throw new IllegalArgumentException("El valor recibido no puede ser menor al valor total de la venta.");
        }
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
