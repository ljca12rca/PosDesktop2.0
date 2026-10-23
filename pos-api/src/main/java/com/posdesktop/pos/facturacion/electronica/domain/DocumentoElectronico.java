package com.posdesktop.pos.facturacion.electronica.domain;

import java.time.LocalDate;
import java.util.List;

/** Modelo canonico: el modulo de ventas lo construira sin conocer a Dataico. */
public record DocumentoElectronico(
        TipoDocumentoElectronico tipo,
        String numero,
        LocalDate fechaEmision,
        String prefijo,
        String moneda,
        AdquirenteElectronico adquirente,
        List<LineaDocumentoElectronico> lineas,
        boolean enviarDian,
        boolean enviarEmail,
        AjustesEmision ajustes
) {
    public DocumentoElectronico(TipoDocumentoElectronico tipo, String numero, LocalDate fechaEmision,
            String prefijo, String moneda, AdquirenteElectronico adquirente, List<LineaDocumentoElectronico> lineas,
            boolean enviarDian, boolean enviarEmail) {
        this(tipo, numero, fechaEmision, prefijo, moneda, adquirente, lineas, enviarDian, enviarEmail, null);
    }
    public DocumentoElectronico {
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }
}
