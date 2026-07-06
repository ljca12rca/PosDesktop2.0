package com.posdesktop.pos.cierres.service;

import com.posdesktop.pos.cierres.api.dto.CierreDiarioListadoResponse;
import com.posdesktop.pos.cierres.api.dto.ResumenCierreDiarioResponse;
import com.posdesktop.pos.modelo.relacional.CierreDiario;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class CierreDiarioMapper {

    public ResumenCierreDiarioResponse toResumenPersistido(CierreDiario cierre) {
        return new ResumenCierreDiarioResponse(
                cierre.getFechaOperacion(),
                cierre.getCantidadVentas(),
                cierre.getSubtotalCalculado(),
                cierre.getTotalCalculado(),
                cierre.getMontoRecibidoCalculado(),
                cierre.getCambioEntregadoCalculado(),
                cierre.getMontoNetoCajaCalculado(),
                cierre.getBaseCaja(),
                cierre.getTrabajadoras(),
                cierre.getAhorro(),
                cierre.getTotalFinal(),
                true,
                cierre.getEstado().name(),
                cierre.getObservacion()
        );
    }

    public ResumenCierreDiarioResponse toResumenBorrador(
            LocalDate fechaOperacion,
            CierreDiarioCalculo calculo
    ) {
        return new ResumenCierreDiarioResponse(
                fechaOperacion,
                calculo.cantidadVentas(),
                calculo.subtotalVentas(),
                calculo.totalVentas(),
                calculo.montoRecibido(),
                calculo.cambioEntregado(),
                calculo.montoNetoCaja(),
                calculo.baseCaja(),
                calculo.trabajadoras(),
                calculo.ahorro(),
                calculo.totalFinal(),
                false,
                "ABIERTO",
                null
        );
    }

    public CierreDiarioListadoResponse toListado(CierreDiario cierre) {
        return new CierreDiarioListadoResponse(
                cierre.getId(),
                cierre.getFechaOperacion(),
                cierre.getFechaHoraCierre(),
                cierre.getCantidadVentas(),
                cierre.getTotalCalculado(),
                cierre.getMontoNetoCajaCalculado(),
                cierre.getBaseCaja(),
                cierre.getTrabajadoras(),
                cierre.getAhorro(),
                cierre.getTotalFinal(),
                cierre.getEstado().name()
        );
    }
}
