package com.posdesktop.pos.invoicing.application;

import java.util.Set;

/** Metadatos de capacidades; no obliga a proveedores a implementar operaciones ficticias. */
public interface ElectronicProvider {
    enum Capability { LOCAL_UBL_DRAFT, LIVE_ISSUANCE, STATUS_LOOKUP }
    ProviderType type();
    Set<Capability> capabilities();
}
