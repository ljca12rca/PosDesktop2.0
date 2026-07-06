package com.posdesktop.pos.shared.service;

import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ModuleStatusResponse;
import com.posdesktop.pos.shared.api.SystemStatusResponse;
import com.posdesktop.pos.shared.config.PosApiProperties;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModuleCatalogService {

    private final PosApiProperties properties;

    public ModuleCatalogService(PosApiProperties properties) {
        this.properties = properties;
    }

    public SystemStatusResponse systemStatus() {
        return new SystemStatusResponse(
                properties.applicationName(),
                properties.version(),
                properties.environment(),
                properties.desktopMockEnabled(),
                availableModules()
        );
    }

    public List<ModuleStatusResponse> availableModules() {
        return List.of(
                module("ventas", "Ventas", ApiPaths.VENTAS, "BOOTSTRAP", "Preparado para logica de ventas manuales y futuras ventas con articulos."),
                module("cierres", "Cierres", ApiPaths.CIERRES, "BOOTSTRAP", "Preparado para cierres diarios y consolidacion de movimientos."),
                module("separados", "Separados", ApiPaths.SEPARADOS, "BOOTSTRAP", "Preparado para apartados, abonos y saldo pendiente."),
                module("proveedores", "Proveedores", ApiPaths.PROVEEDORES, "BOOTSTRAP", "Preparado para administrar proveedores base."),
                module("facturas-proveedor", "Facturas proveedor", ApiPaths.FACTURAS, "BOOTSTRAP", "Preparado para deuda, pagos y soportes."),
                module("articulos", "Articulos", ApiPaths.ARTICULOS, "BOOTSTRAP", "Preparado para catalogo e integracion futura con ventas.")
        );
    }

    public ModuleStatusResponse moduleByCode(String code) {
        return availableModules().stream()
                .filter(module -> module.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe un modulo registrado con codigo " + code + "."));
    }

    private ModuleStatusResponse module(String code, String name, String basePath, String stage, String summary) {
        return new ModuleStatusResponse(code, name, basePath, stage, summary);
    }
}
