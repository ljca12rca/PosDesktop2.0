package com.posdesktop.pos.facturacion.electronica.integration;

import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.invoicing.application.ElectronicProvider;
import com.posdesktop.pos.invoicing.application.ProviderType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Puente incremental al contrato existente. Nunca reimplementa JSON, HTTP o autenticacion. */
@Component
public final class DataicoLegacyAdapter implements ElectronicProvider {
    private final FacturacionElectronicaService legacy;
    public DataicoLegacyAdapter(FacturacionElectronicaService legacy) { this.legacy = legacy; }
    @Override public ProviderType type() { return ProviderType.DATAICO; }
    @Override public Set<Capability> capabilities() { return Set.of(Capability.LIVE_ISSUANCE, Capability.STATUS_LOOKUP); }

    // Mantiene el contrato anterior hasta verificar un mapeo fiscal completo hacia Dataico.
    public ResultadoEmisionDocumento emitExisting(DocumentoElectronico document) {
        return legacy.emitir("dataico", document);
    }
}
