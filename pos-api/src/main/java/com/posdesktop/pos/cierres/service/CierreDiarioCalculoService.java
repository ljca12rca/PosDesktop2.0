package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import com.posdesktop.pos.modelo.enumeraciones.MedioPagoVenta;
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
        BigDecimal baseNormalizada = normalizar(baseCaja);
        BigDecimal trabajadorasNormalizado = normalizar(trabajadoras);
        BigDecimal ahorroNormalizado = normalizar(ahorro);
        BigDecimal efectivoRecibido = sumar(ventasValidas.stream().map(this::montoRecibidoEfectivo).toList());
        BigDecimal transferenciasRecibidas = sumar(ventasValidas.stream().map(this::montoRecibidoTransferencia).toList());
        BigDecimal cambioEfectivo = sumar(ventasValidas.stream().map(this::cambioEntregadoEfectivo).toList());
        BigDecimal efectivoNeto = efectivoRecibido
                .subtract(cambioEfectivo)
                .subtract(baseNormalizada)
                .subtract(trabajadorasNormalizado)
                .subtract(ahorroNormalizado)
                .setScale(2, RoundingMode.HALF_UP);

        return new CierreDiarioCalculo(
                ventasValidas.size(),
                sumar(ventasValidas.stream().map(Venta::getSubtotal).toList()),
                sumar(ventasValidas.stream().map(Venta::getDescuentoTotal).toList()),
                sumar(ventasValidas.stream().map(Venta::getImpuestoTotal).toList()),
                sumar(ventasValidas.stream().map(Venta::getTotal).toList()),
                efectivoRecibido.add(transferenciasRecibidas).setScale(2, RoundingMode.HALF_UP),
                cambioEfectivo,
                efectivoNeto,
                baseNormalizada,
                trabajadorasNormalizado,
                ahorroNormalizado,
                efectivoNeto.add(transferenciasRecibidas).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal sumar(List<BigDecimal> valores) {
        return valores.stream()
                .map(this::normalizar)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal montoRecibidoEfectivo(Venta venta) {
        if (venta == null || venta.getMedioPago() == MedioPagoVenta.TRANSFERENCIA_QR) {
            return BigDecimal.ZERO;
        }
        return montoRecibidoOTotal(venta);
    }

    private BigDecimal montoRecibidoTransferencia(Venta venta) {
        if (venta == null || venta.getMedioPago() != MedioPagoVenta.TRANSFERENCIA_QR) {
            return BigDecimal.ZERO;
        }
        return montoRecibidoOTotal(venta);
    }

    private BigDecimal montoRecibidoOTotal(Venta venta) {
        BigDecimal recibido = normalizar(venta.getMontoRecibido());
        return recibido.signum() > 0 ? recibido : normalizar(venta.getTotal());
    }

    private BigDecimal cambioEntregadoEfectivo(Venta venta) {
        if (venta == null || venta.getMedioPago() == MedioPagoVenta.TRANSFERENCIA_QR) {
            return BigDecimal.ZERO;
        }
        return normalizar(venta.getCambioEntregado()).max(BigDecimal.ZERO);
    }

    private BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
