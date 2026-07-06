package com.posdesktop.pos.shared.api;

public record ModuleStatusResponse(
        String code,
        String name,
        String basePath,
        String stage,
        String summary
) {
}
