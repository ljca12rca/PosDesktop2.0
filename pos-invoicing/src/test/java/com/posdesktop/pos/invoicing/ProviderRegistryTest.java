package com.posdesktop.pos.invoicing;

import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.application.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.UblDraftGenerator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
    @Test void dataicoPredeterminadoYDirectoSinEmision() {
        assertEquals(ProviderType.DATAICO, new InvoicingSettings("instalacion-prueba", null).provider());
        ElectronicProvider legacy = new ElectronicProvider() {
            public ProviderType type() { return ProviderType.DATAICO; }
            public Set<Capability> capabilities() { return Set.of(Capability.LIVE_ISSUANCE); }
        };
        var registry = new ProviderRegistry(List.of(legacy, new UblDraftGenerator()));
        assertSame(legacy, registry.require(ProviderType.DATAICO, ElectronicProvider.Capability.LIVE_ISSUANCE));
        assertThrows(UnsupportedOperationException.class, () -> registry.require(ProviderType.DIAN_DIRECT, ElectronicProvider.Capability.LIVE_ISSUANCE));
        assertEquals(ProviderType.DIAN_DIRECT, registry.require(ProviderType.DIAN_DIRECT, ElectronicProvider.Capability.LOCAL_UBL_DRAFT).type());
    }
    @Test void rechazaProveedoresDuplicados() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(List.of(new UblDraftGenerator(), new UblDraftGenerator())));
    }
}
