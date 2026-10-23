package com.posdesktop.pos.ventas.service;

import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import com.posdesktop.pos.repositorio.relacional.FacturaElectronicaRepositorio;
import com.posdesktop.pos.ventas.api.dto.FacturaElectronicaListadoResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacturasElectronicasConsultaService {
    private final FacturaElectronicaRepositorio facturaElectronicaRepositorio;

    public FacturasElectronicasConsultaService(FacturaElectronicaRepositorio facturaElectronicaRepositorio) {
        this.facturaElectronicaRepositorio = facturaElectronicaRepositorio;
    }

    @Transactional(readOnly = true)
    public List<FacturaElectronicaListadoResponse> listar(
            String numeracion, String factura, LocalDate fechaInicial, LocalDate fechaFinal,
            String cliente, String estadoDian, String estadoCliente, String estadoCorreo
    ) {
        LocalDate inicio = fechaInicial == null ? LocalDate.now() : fechaInicial;
        LocalDate fin = fechaFinal == null ? inicio : fechaFinal;
        if (fin.isBefore(inicio)) throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        return facturaElectronicaRepositorio.findAllByOrderByFechaEmisionDescNumeroFacturaDesc().stream()
                .filter(value -> !value.getFechaEmision().toLocalDate().isBefore(inicio) && !value.getFechaEmision().toLocalDate().isAfter(fin))
                .filter(value -> coincide(value.getNumeracion(), numeracion))
                .filter(value -> coincide(value.getNumeroFactura(), factura))
                .filter(value -> coincide(value.getClienteNombre(), cliente) || coincide(value.getClienteIdentificacion(), cliente))
                .filter(value -> coincideEstado(value.getEstadoDian(), estadoDian))
                .filter(value -> coincideEstado(value.getEstadoCliente(), estadoCliente))
                .filter(value -> coincideEstado(value.getEstadoCorreo(), estadoCorreo))
                .map(this::mapear)
                .toList();
    }

    private boolean coincide(String value, String filter) {
        return filter == null || filter.isBlank() || "TODAS".equalsIgnoreCase(filter)
                || value != null && value.toLowerCase(java.util.Locale.ROOT).contains(filter.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private boolean coincideEstado(String value, String filter) {
        return filter == null || filter.isBlank() || "TODOS".equalsIgnoreCase(filter)
                || value != null && value.equalsIgnoreCase(filter.trim());
    }

    FacturaElectronicaListadoResponse mapear(FacturaElectronica value) {
        return new FacturaElectronicaListadoResponse(value.getId().toString(), value.getFechaEmision(), value.getNumeracion(),
                value.getNumeroFactura(), value.getClienteNombre(), value.getClienteIdentificacion(), value.getImpuestoTotal(),
                value.getRetencionTotal(), value.getTotal(), value.getEstadoDian(), value.getEstadoCliente(),
                value.getEstadoCorreo(), value.getMensajeDian(), value.getMensajeCorreo(), value.getPdfUrl(), value.getClienteEmail());
    }
}
