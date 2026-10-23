package com.posdesktop.pos.facturacion.electronica.application;

import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.facturacion.electronica.domain.TerceroDian;
import java.util.List;
import org.springframework.stereotype.Service;

/** Caso de uso sin acoplamiento a ventas, UI ni a un proveedor especifico. */
@Service
public class FacturacionElectronicaService {
    private final List<ProveedorFacturacionElectronica> proveedores;

    public FacturacionElectronicaService(List<ProveedorFacturacionElectronica> proveedores) {
        this.proveedores = List.copyOf(proveedores);
    }

    public ResultadoEmisionDocumento emitir(String codigoProveedor, DocumentoElectronico documento) {
        return proveedor(codigoProveedor).emitir(documento);
    }

    public ResultadoEmisionDocumento consultarFactura(String codigoProveedor, String numeroFactura) {
        return proveedor(codigoProveedor).consultarFactura(numeroFactura);
    }

    public TerceroDian consultarTerceroDian(String codigoProveedor, String tipoIdentificacion, String identificacion) {
        return proveedor(codigoProveedor).consultarTerceroDian(tipoIdentificacion, identificacion);
    }

    public TerceroDian consultarTerceroDian(String codigoProveedor, String identificacion) {
        return proveedor(codigoProveedor).consultarTerceroDian(identificacion);
    }

    public void reenviar(String codigoProveedor, String identificadorExterno, boolean enviarDian, boolean enviarEmail, String email) {
        proveedor(codigoProveedor).reenviar(identificadorExterno, enviarDian, enviarEmail, email);
    }

    private ProveedorFacturacionElectronica proveedor(String codigoProveedor) {
        return proveedores.stream()
                .filter(proveedor -> proveedor.codigoProveedor().equalsIgnoreCase(codigoProveedor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Proveedor de facturacion electronica no configurado: " + codigoProveedor));
    }
}
