package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.modelo.relacional.CierreDiario;
import com.posdesktop.pos.modelo.relacional.Venta;
import java.math.BigDecimal;
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
        CierreDiario cierreBorrador = new CierreDiario();
        cierreBorrador.setBaseCaja(baseCaja);
        cierreBorrador.setTrabajadoras(trabajadoras);
        cierreBorrador.setAhorro(ahorro);
        cierreBorrador.reemplazarVentas(ventas == null ? List.of() : ventas);

        return new CierreDiarioCalculo(
                cierreBorrador.getCantidadVentas(),
                cierreBorrador.getSubtotalCalculado(),
                cierreBorrador.getDescuentoCalculado(),
                cierreBorrador.getImpuestoCalculado(),
                cierreBorrador.getTotalCalculado(),
                cierreBorrador.getMontoRecibidoCalculado(),
                cierreBorrador.getCambioEntregadoCalculado(),
                cierreBorrador.getMontoNetoCajaCalculado(),
                cierreBorrador.getBaseCaja(),
                cierreBorrador.getTrabajadoras(),
                cierreBorrador.getAhorro(),
                cierreBorrador.getTotalFinal()
        );
    }
}
