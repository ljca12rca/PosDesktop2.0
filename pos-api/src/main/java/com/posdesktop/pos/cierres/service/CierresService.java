package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.cierres.api.dto.CierreDiarioListadoResponse;
import com.posdesktop.pos.cierres.api.dto.RegistrarCierreRequest;
import com.posdesktop.pos.cierres.api.dto.ResumenCierreDiarioResponse;
import com.posdesktop.pos.modelo.enumeraciones.EstadoCierreDiario;
import com.posdesktop.pos.modelo.relacional.CierreDiario;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.CierreDiarioRepositorio;
import com.posdesktop.pos.ventas.service.VentasService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CierresService {

    private final CierreDiarioRepositorio cierreDiarioRepositorio;
    private final VentasService ventasService;

    public CierresService(CierreDiarioRepositorio cierreDiarioRepositorio, VentasService ventasService) {
        this.cierreDiarioRepositorio = cierreDiarioRepositorio;
        this.ventasService = ventasService;
    }

    @Transactional(readOnly = true)
    public ResumenCierreDiarioResponse consultarResumen(LocalDate fechaOperacion) {
        LocalDate fecha = fechaOperacion == null ? LocalDate.now() : fechaOperacion;
        List<Venta> ventas = ventasService.ventasDelDia(fecha);
        CierreDiario cierre = cierreDiarioRepositorio.findByFechaOperacion(fecha).orElse(null);

        BigDecimal subtotalVentas = sumar(ventas.stream().map(Venta::getSubtotal).toList());
        BigDecimal totalVentas = sumar(ventas.stream().map(Venta::getTotal).toList());
        BigDecimal montoRecibido = sumar(ventas.stream().map(Venta::getMontoRecibido).toList());
        BigDecimal cambioEntregado = sumar(ventas.stream().map(Venta::getCambioEntregado).toList());
        BigDecimal baseCaja = cierre == null ? BigDecimal.ZERO : cierre.getBaseCaja();
        BigDecimal egresos = cierre == null ? BigDecimal.ZERO : cierre.getEgresos();
        BigDecimal totalFinal = totalVentas.add(baseCaja).subtract(egresos);

        return new ResumenCierreDiarioResponse(
                fecha,
                ventas.size(),
                subtotalVentas,
                totalVentas,
                montoRecibido,
                cambioEntregado,
                baseCaja,
                egresos,
                totalFinal,
                cierre != null,
                cierre == null ? null : cierre.getResponsable(),
                cierre == null ? EstadoCierreDiario.ABIERTO.name() : cierre.getEstado().name(),
                cierre == null ? null : cierre.getObservacion()
        );
    }

    @Transactional
    public ResumenCierreDiarioResponse registrarCierre(RegistrarCierreRequest request) {
        CierreDiario cierre = cierreDiarioRepositorio.findByFechaOperacion(request.fechaOperacion())
                .orElseGet(CierreDiario::new);

        cierre.setFechaOperacion(request.fechaOperacion());
        cierre.setFechaHoraCierre(LocalDateTime.now());
        cierre.setResponsable(request.responsable());
        cierre.setBaseCaja(normalizar(request.base()));
        cierre.setEgresos(normalizar(request.egresos()));
        cierre.setObservacion(request.observacion());
        cierre.setEstado(EstadoCierreDiario.CERRADO);

        List<Venta> ventasDelDia = ventasService.ventasDelDia(request.fechaOperacion());
        for (Venta venta : ventasDelDia) {
            cierre.agregarVenta(venta);
        }
        cierre.recalcularTotales();

        CierreDiario cierreGuardado = cierreDiarioRepositorio.save(cierre);
        return new ResumenCierreDiarioResponse(
                cierreGuardado.getFechaOperacion(),
                cierreGuardado.getCantidadVentas(),
                cierreGuardado.getSubtotalCalculado(),
                cierreGuardado.getTotalCalculado(),
                sumar(ventasDelDia.stream().map(Venta::getMontoRecibido).toList()),
                sumar(ventasDelDia.stream().map(Venta::getCambioEntregado).toList()),
                cierreGuardado.getBaseCaja(),
                cierreGuardado.getEgresos(),
                cierreGuardado.getTotalFinal(),
                true,
                cierreGuardado.getResponsable(),
                cierreGuardado.getEstado().name(),
                cierreGuardado.getObservacion()
        );
    }

    @Transactional(readOnly = true)
    public List<CierreDiarioListadoResponse> listarCierres(LocalDate fechaInicial, LocalDate fechaFinal) {
        LocalDate inicio = fechaInicial == null ? LocalDate.now().minusDays(7) : fechaInicial;
        LocalDate fin = fechaFinal == null ? LocalDate.now() : fechaFinal;
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        }

        return cierreDiarioRepositorio.findByFechaOperacionBetweenOrderByFechaOperacionDesc(inicio, fin)
                .stream()
                .map(this::mapearListado)
                .toList();
    }

    private CierreDiarioListadoResponse mapearListado(CierreDiario cierre) {
        return new CierreDiarioListadoResponse(
                cierre.getId(),
                cierre.getFechaOperacion(),
                cierre.getFechaHoraCierre(),
                cierre.getResponsable(),
                cierre.getCantidadVentas(),
                cierre.getTotalCalculado(),
                cierre.getBaseCaja(),
                cierre.getEgresos(),
                cierre.getTotalFinal(),
                cierre.getEstado().name()
        );
    }

    private BigDecimal sumar(List<BigDecimal> valores) {
        return valores.stream()
                .map(this::normalizar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
