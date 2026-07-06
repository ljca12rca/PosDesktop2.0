package com.posdesktop.pos.shared.api;

import java.util.List;

public record SystemStatusResponse(
        String application,
        String version,
        String environment,
        boolean desktopMockEnabled,
        List<ModuleStatusResponse> modules
) {
}
