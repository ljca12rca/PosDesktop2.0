package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.cierres.api.dto.CierreDiarioListadoResponse;
import com.posdesktop.pos.cierres.api.dto.RegistrarCierreRequest;
import com.posdesktop.pos.cierres.api.dto.ResumenCierreDiarioResponse;
import com.posdesktop.pos.modelo.enumeraciones.EstadoCierreDiario;
import com.posdesktop.pos.modelo.relacional.CierreDiario;
import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.CierreDiarioRepositorio;
import com.posdesktop.pos.ventas.service.VentasService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CierresService {

    private final CierreDiarioRepositorio cierreDiarioRepositorio;
    private final VentasService ventasService;
    private final CierreDiarioCalculoService cierreDiarioCalculoService;
    private final CierreDiarioValidationService cierreDiarioValidationService;
    private final CierreDiarioMapper cierreDiarioMapper;
    private final CierreDiarioAutoSyncService cierreDiarioAutoSyncService;

    public CierresService(
            CierreDiarioRepositorio cierreDiarioRepositorio,
            VentasService ventasService,
            CierreDiarioCalculoService cierreDiarioCalculoService,
            CierreDiarioValidationService cierreDiarioValidationService,
            CierreDiarioMapper cierreDiarioMapper,
            CierreDiarioAutoSyncService cierreDiarioAutoSyncService
    ) {
        this.cierreDiarioRepositorio = cierreDiarioRepositorio;
        this.ventasService = ventasService;
        this.cierreDiarioCalculoService = cierreDiarioCalculoService;
        this.cierreDiarioValidationService = cierreDiarioValidationService;
        this.cierreDiarioMapper = cierreDiarioMapper;
        this.cierreDiarioAutoSyncService = cierreDiarioAutoSyncService;
    }

    @Transactional
    public ResumenCierreDiarioResponse consultarResumen(LocalDate fechaOperacion) {
        LocalDate fecha = fechaOperacion == null ? LocalDate.now() : fechaOperacion;
        cierreDiarioValidationService.validarConsulta(fecha);

        CierreDiario cierre = refrescarCierreDeHoySiAplica(fecha)
                .orElseGet(() -> cierreDiarioRepositorio.findByFechaOperacion(fecha).orElse(null));
        if (cierre != null) {
            return cierreDiarioMapper.toResumenPersistido(cierre);
        }

        List<Venta> ventas = ventasService.ventasDelDia(fecha);
        CierreDiarioCalculo calculo = cierreDiarioCalculoService.calcular(ventas, null, null, null);
        return cierreDiarioMapper.toResumenBorrador(fecha, calculo);
    }

    @Transactional
    public ResumenCierreDiarioResponse registrarCierre(RegistrarCierreRequest request) {
        cierreDiarioValidationService.validarRegistro(request);

        CierreDiario cierre = cierreDiarioRepositorio.findByFechaOperacion(request.fechaOperacion())
                .orElseGet(CierreDiario::new);

        cierre.setFechaOperacion(request.fechaOperacion());
        cierre.setFechaHoraCierre(LocalDateTime.now());
        cierre.setBaseCaja(normalizar(request.base()));
        cierre.setTrabajadoras(normalizar(request.trabajadoras()));
        cierre.setAhorro(normalizar(request.ahorro()));
        cierre.setObservacion(request.observacion());
        cierre.setEstado(EstadoCierreDiario.CERRADO);

        List<Venta> ventasDelDia = ventasService.ventasDelDia(request.fechaOperacion());
        ventasDelDia.stream()
                .filter(venta -> venta.getEstado() != EstadoVenta.ANULADA)
                .forEach(venta -> venta.setEstado(EstadoVenta.CERRADA));
        cierre.reemplazarVentas(ventasDelDia);

        CierreDiario cierreGuardado = cierreDiarioRepositorio.save(cierre);
        return cierreDiarioMapper.toResumenPersistido(cierreGuardado);
    }

    @Transactional
    public List<CierreDiarioListadoResponse> listarCierres(LocalDate fechaInicial, LocalDate fechaFinal) {
        LocalDate inicio = fechaInicial == null ? LocalDate.now().minusDays(7) : fechaInicial;
        LocalDate fin = fechaFinal == null ? LocalDate.now() : fechaFinal;
        cierreDiarioValidationService.validarRango(inicio, fin);
        if (!LocalDate.now().isBefore(inicio) && !LocalDate.now().isAfter(fin)) {
            refrescarCierreDeHoySiAplica(LocalDate.now());
        }

        return cierreDiarioRepositorio.findByFechaOperacionBetweenOrderByFechaOperacionDesc(inicio, fin)
                .stream()
                .map(cierreDiarioMapper::toListado)
                .toList();
    }

    private java.util.Optional<CierreDiario> refrescarCierreDeHoySiAplica(LocalDate fechaOperacion) {
        if (!LocalDate.now().equals(fechaOperacion)) {
            return java.util.Optional.empty();
        }
        return cierreDiarioAutoSyncService.sincronizarYObtenerCierreDelDia(fechaOperacion);
    }

    private BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
