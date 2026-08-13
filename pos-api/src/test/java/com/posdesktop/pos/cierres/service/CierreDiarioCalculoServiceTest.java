package com.posdesktop.pos.cierres.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.posdesktop.pos.modelo.enumeraciones.EstadoCierreDiario;
import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import com.posdesktop.pos.modelo.enumeraciones.MedioPagoVenta;
import com.posdesktop.pos.modelo.relacional.CierreDiario;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.CierreDiarioRepositorio;
import com.posdesktop.pos.repositorio.relacional.VentaRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CierreDiarioCalculoServiceTest {

    private final CierreDiarioCalculoService calculoService = new CierreDiarioCalculoService();

    @Test
    void incluyeTransferenciasEnElTotalFinalYPreservaElEfectivoNetoDeCaja() {
        Venta ventaEfectivo = venta("10000", "15000", "5000", MedioPagoVenta.EFECTIVO);
        Venta ventaTransferencia = venta("30000", "30000", "0", MedioPagoVenta.TRANSFERENCIA_QR);

        CierreDiarioCalculo calculo = calculoService.calcular(
                List.of(ventaEfectivo, ventaTransferencia),
                dinero("1000"),
                dinero("2000"),
                dinero("3000")
        );

        assertThat(calculo.totalVentas()).isEqualByComparingTo("40000.00");
        assertThat(calculo.montoRecibido()).isEqualByComparingTo("45000.00");
        assertThat(calculo.cambioEntregado()).isEqualByComparingTo("5000.00");
        assertThat(calculo.montoNetoCaja()).isEqualByComparingTo("4000.00");
        assertThat(calculo.totalFinal()).isEqualByComparingTo("34000.00");
        assertThat(ventaEfectivo.getCierreDiario()).isNull();
        assertThat(ventaTransferencia.getCierreDiario()).isNull();
    }

    @Test
    void sincronizaElCierreGuardadoConCadaVentaDelDiaSinCambiarSuFechaDeCierre() {
        LocalDate fechaOperacion = LocalDate.of(2026, 8, 13);
        LocalDateTime fechaHoraOriginal = LocalDateTime.of(2026, 8, 13, 12, 30);
        CierreDiario cierre = new CierreDiario();
        cierre.setFechaOperacion(fechaOperacion);
        cierre.setFechaHoraCierre(fechaHoraOriginal);
        cierre.setEstado(EstadoCierreDiario.CERRADO);
        cierre.setBaseCaja(dinero("1000"));

        Venta ventaEfectivo = venta("10000", "10000", "0", MedioPagoVenta.EFECTIVO);
        Venta ventaTransferencia = venta("20000", "20000", "0", MedioPagoVenta.TRANSFERENCIA_QR);
        CierreDiarioRepositorio cierreRepositorio = Mockito.mock(CierreDiarioRepositorio.class);
        VentaRepositorio ventaRepositorio = Mockito.mock(VentaRepositorio.class);
        when(cierreRepositorio.findByFechaOperacion(fechaOperacion)).thenReturn(java.util.Optional.of(cierre));
        when(ventaRepositorio.findByFechaVentaBetween(
                fechaOperacion.atStartOfDay(), fechaOperacion.atTime(java.time.LocalTime.MAX)
        )).thenReturn(List.of(ventaEfectivo, ventaTransferencia));
        when(cierreRepositorio.saveAndFlush(cierre)).thenReturn(cierre);

        CierreDiarioAutoSyncService autoSyncService = new CierreDiarioAutoSyncService(
                cierreRepositorio,
                ventaRepositorio
        );

        autoSyncService.sincronizarSiCierreDelDiaYaExiste(fechaOperacion);

        assertThat(cierre.getTotalFinal()).isEqualByComparingTo("29000.00");
        assertThat(cierre.getMontoNetoCajaCalculado()).isEqualByComparingTo("9000.00");
        assertThat(cierre.getFechaHoraCierre()).isEqualTo(fechaHoraOriginal);
        assertThat(ventaEfectivo.getEstado()).isEqualTo(EstadoVenta.CERRADA);
        assertThat(ventaTransferencia.getEstado()).isEqualTo(EstadoVenta.CERRADA);
        verify(cierreRepositorio).saveAndFlush(cierre);
    }

    private Venta venta(
            String total,
            String montoRecibido,
            String cambioEntregado,
            MedioPagoVenta medioPago
    ) {
        Venta venta = new Venta();
        venta.registrarVentaManual("Venta de prueba", dinero(total));
        venta.setMontoRecibido(dinero(montoRecibido));
        venta.setCambioEntregado(dinero(cambioEntregado));
        venta.setMedioPago(medioPago);
        return venta;
    }

    private BigDecimal dinero(String valor) {
        return new BigDecimal(valor);
    }
}
