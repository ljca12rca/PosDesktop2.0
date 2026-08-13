package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.modelo.enumeraciones.EstadoCierreDiario;
import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import com.posdesktop.pos.modelo.relacional.CierreDiario;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.CierreDiarioRepositorio;
import com.posdesktop.pos.repositorio.relacional.VentaRepositorio;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CierreDiarioAutoSyncService {

    private final CierreDiarioRepositorio cierreDiarioRepositorio;
    private final VentaRepositorio ventaRepositorio;

    public CierreDiarioAutoSyncService(
            CierreDiarioRepositorio cierreDiarioRepositorio,
            VentaRepositorio ventaRepositorio
    ) {
        this.cierreDiarioRepositorio = cierreDiarioRepositorio;
        this.ventaRepositorio = ventaRepositorio;
    }

    @Transactional
    public void sincronizarSiCierreDelDiaYaExiste(LocalDate fechaOperacion) {
        sincronizarYObtenerCierreDelDia(fechaOperacion);
    }

    @Transactional
    public Optional<CierreDiario> sincronizarYObtenerCierreDelDia(LocalDate fechaOperacion) {
        return cierreDiarioRepositorio.findByFechaOperacion(fechaOperacion)
                .filter(cierre -> cierre.getEstado() == EstadoCierreDiario.CERRADO)
                .map(cierre -> {
                    List<Venta> ventasDelDia = ventaRepositorio.findByFechaVentaBetween(
                            fechaOperacion.atStartOfDay(),
                            fechaOperacion.atTime(LocalTime.MAX)
                    );

                    ventasDelDia.stream()
                            .filter(venta -> venta.getEstado() != EstadoVenta.ANULADA)
                            .forEach(venta -> venta.setEstado(EstadoVenta.CERRADA));

                    cierre.reemplazarVentas(ventasDelDia);
                    cierreDiarioRepositorio.saveAndFlush(cierre);
                    return cierre;
                });
    }
}
