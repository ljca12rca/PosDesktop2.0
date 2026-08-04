package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import com.posdesktop.pos.modelo.relacional.Venta;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CierreDiarioCalculoService {

    public CierreDiarioCalculo calcular(
            List<Venta> ventas,
            BigDecimal baseCaja,
            BigDecimal trabajadoras,
            BigDecimal ahorro
    ) {
        List<Venta> ventasValidas = ventas == null ? List.of() : ventas.stream()
                .filter(venta -> venta != null && venta.getEstado() != EstadoVenta.ANULADA)
                .toList();

        BigDecimal subtotalVentas = sumar(ventasValidas.stream().map(Venta::getSubtotal).toList());
        BigDecimal descuentoVentas = sumar(ventasValidas.stream().map(Venta::getDescuentoTotal).toList());
        BigDecimal impuestoVentas = sumar(ventasValidas.stream().map(Venta::getImpuestoTotal).toList());
        BigDecimal totalVentas = sumar(ventasValidas.stream().map(Venta::getTotal).toList());
        BigDecimal montoRecibido = sumar(ventasValidas.stream().map(this::montoRecibidoEfectivo).toList());
        BigDecimal cambioEntregado = sumar(ventasValidas.stream().map(this::cambioEntregadoEfectivo).toList());
        BigDecimal baseNormalizada = normalizar(baseCaja);
        BigDecimal trabajadorasNormalizado = normalizar(trabajadoras);
        BigDecimal ahorroNormalizado = normalizar(ahorro);
        BigDecimal montoNetoCaja = montoRecibido
                .subtract(baseNormalizada)
                .subtract(trabajadorasNormalizado)
                .subtract(ahorroNormalizado)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFinal = montoRecibido
                .subtract(cambioEntregado)
                .subtract(trabajadorasNormalizado)
                .subtract(ahorroNormalizado)
                .subtract(baseNormalizada)
                .setScale(2, RoundingMode.HALF_UP);

        return new CierreDiarioCalculo(
                ventasValidas.size(),
                subtotalVentas,
                descuentoVentas,
                impuestoVentas,
                totalVentas,
                montoRecibido,
                cambioEntregado,
                montoNetoCaja,
                baseNormalizada,
                trabajadorasNormalizado,
                ahorroNormalizado,
                totalFinal
        );
    }

    private BigDecimal sumar(List<BigDecimal> valores) {
        return valores.stream()
                .map(this::normalizar)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal montoRecibidoEfectivo(Venta venta) {
        if (venta != null && venta.getMedioPago() == com.posdesktop.pos.modelo.enumeraciones.MedioPagoVenta.TRANSFERENCIA_QR) {
            return BigDecimal.ZERO;
        }
        BigDecimal recibido = normalizar(venta == null ? null : venta.getMontoRecibido());
        if (recibido.signum() > 0) {
            return recibido;
        }
        return normalizar(venta == null ? null : venta.getTotal());
    }

    private BigDecimal cambioEntregadoEfectivo(Venta venta) {
        if (venta != null && venta.getMedioPago() == com.posdesktop.pos.modelo.enumeraciones.MedioPagoVenta.TRANSFERENCIA_QR) {
            return BigDecimal.ZERO;
        }
        return normalizar(venta == null ? null : venta.getCambioEntregado()).max(BigDecimal.ZERO);
    }

    private BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
