package com.posdesktop.pos.invoicing.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ProviderRegistry {
    private final Map<ProviderType, ElectronicProvider> providers;

    public ProviderRegistry(List<ElectronicProvider> providers) {
        var indexed = new EnumMap<ProviderType, ElectronicProvider>(ProviderType.class);
        for (var provider : providers) {
            if (indexed.putIfAbsent(provider.type(), provider) != null) {
                throw new IllegalArgumentException("Proveedor duplicado: " + provider.type());
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public ElectronicProvider require(ProviderType type, ElectronicProvider.Capability capability) {
        var selected = providers.get(type);
        if (selected == null || !selected.capabilities().contains(capability)) {
            throw new UnsupportedOperationException("Capacidad no habilitada: " + type + "/" + capability);
        }
        return selected; // Nunca fallback, seleccion doble ni emisiones como efecto secundario.
    }
}
