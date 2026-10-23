package com.posdesktop.pos.ventas.service;

import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import com.posdesktop.pos.repositorio.relacional.FacturaElectronicaRepositorio;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import com.posdesktop.pos.ventas.api.dto.FacturaElectronicaListadoResponse;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Acciones sobre el UUID existente en Dataico: nunca crea otra factura ni consume consecutivos. */
@Service
public class FacturasElectronicasAccionesService {
    private static final Logger log = LoggerFactory.getLogger(FacturasElectronicasAccionesService.class);
    private final FacturaElectronicaRepositorio facturas;
    private final FacturacionElectronicaService dataico;
    private final FacturasElectronicasConsultaService consulta;

    public FacturasElectronicasAccionesService(FacturaElectronicaRepositorio facturas,
            FacturacionElectronicaService dataico, FacturasElectronicasConsultaService consulta) {
        this.facturas = facturas;
        this.dataico = dataico;
        this.consulta = consulta;
    }

    @Transactional
    public FacturaElectronicaListadoResponse enviarDian(UUID id) {
        FacturaElectronica factura = factura(id);
        if ("DIAN_ACEPTADO".equalsIgnoreCase(factura.getEstadoDian())) {
            return consulta.mapear(factura);
        }
        dataico.reenviar("dataico", factura.getIdentificadorDataico(), true, false, null);
        factura.setEstadoDian("DIAN_PENDIENTE");
        factura.setMensajeDian("Solicitud de envío a DIAN recibida por Dataico. Pendiente de confirmación.");
        actualizarEstado(factura);
        return consulta.mapear(facturas.saveAndFlush(factura));
    }

    @Transactional
    public FacturaElectronicaListadoResponse enviarCorreo(UUID id, String email) {
        if (email == null || email.isBlank() || email.trim().length() > 120) {
            throw new IllegalArgumentException("Ingresa un correo válido de hasta 120 caracteres.");
        }
        FacturaElectronica factura = factura(id);
        String destino = email.trim();
        dataico.reenviar("dataico", factura.getIdentificadorDataico(), false, true, destino);
        factura.setClienteEmail(destino);
        factura.setEstadoCorreo("PENDIENTE");
        factura.setMensajeCorreo("Solicitud de envío de correo recibida por Dataico. Pendiente de confirmación.");
        actualizarEstado(factura);
        return consulta.mapear(facturas.saveAndFlush(factura));
    }

    private FacturaElectronica factura(UUID id) {
        FacturaElectronica factura = facturas.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la factura electrónica seleccionada."));
        if (factura.getIdentificadorDataico() == null || factura.getIdentificadorDataico().isBlank()) {
            throw new IllegalArgumentException("La factura no tiene un identificador de Dataico para realizar el envío.");
        }
        return factura;
    }

    private void actualizarEstado(FacturaElectronica factura) {
        ResultadoEmisionDocumento estado;
        try {
            estado = dataico.consultarFactura("dataico", factura.getNumeroFactura());
        } catch (RuntimeException exception) {
            // Un fallo de consulta posterior no convierte en fallido el PUT ya recibido por Dataico.
            log.warn("Envío solicitado para factura {}, sin confirmar su estado: {}",
                    factura.getNumeroFactura(), exception.getMessage());
            return;
        }
        if (estado.estado() != null && !estado.estado().isBlank()) {
            factura.setEstadoDian(estado.estado());
            if (estado.mensaje() != null && !estado.mensaje().isBlank()) factura.setMensajeDian(estado.mensaje());
        }
        if (estado.pdfUrl() != null && !estado.pdfUrl().isBlank()) factura.setPdfUrl(estado.pdfUrl());
        String correo = estado.estadoCorreo() == null ? "" : estado.estadoCorreo().trim().toUpperCase(Locale.ROOT);
        switch (correo) {
            case "CORREO_ENVIADO", "ENVIADO" -> {
                factura.setEstadoCorreo("ENVIADO");
                factura.setMensajeCorreo("Dataico confirmó el envío del correo.");
            }
            case "CORREO_FALLO", "ERROR" -> {
                factura.setEstadoCorreo("ERROR");
                factura.setMensajeCorreo("Dataico reportó un error al enviar el correo.");
            }
            case "CORREO_PENDIENTE", "PENDIENTE" -> factura.setEstadoCorreo("PENDIENTE");
            case "CORREO_NO_ENVIADO", "NO_ENVIADO" -> {
                if (!"NO_SOLICITADO".equals(factura.getEstadoCorreo())) factura.setEstadoCorreo("NO_ENVIADO");
            }
            default -> { }
        }
    }
}
