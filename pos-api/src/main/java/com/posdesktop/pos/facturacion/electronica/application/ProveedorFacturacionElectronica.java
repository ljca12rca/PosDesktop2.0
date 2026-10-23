package com.posdesktop.pos.facturacion.electronica.application;

import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.facturacion.electronica.domain.TerceroDian;

/** Puerto de salida. Cada proveedor implementa exclusivamente este contrato. */
public interface ProveedorFacturacionElectronica {
    String codigoProveedor();

    ResultadoEmisionDocumento emitir(DocumentoElectronico documento);

    ResultadoEmisionDocumento consultarFactura(String numeroFactura);

    TerceroDian consultarTerceroDian(String tipoIdentificacion, String identificacion);

    TerceroDian consultarTerceroDian(String identificacion);

    void reenviar(String identificadorExterno, boolean enviarDian, boolean enviarEmail, String email);
}
