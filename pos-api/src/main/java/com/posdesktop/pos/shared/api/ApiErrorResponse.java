package com.posdesktop.pos.shared.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        boolean success,
        String message,
        String path,
        OffsetDateTime timestamp,
        List<String> details
) {

    public static ApiErrorResponse of(String message, String path, List<String> details) {
        return new ApiErrorResponse(false, message, path, OffsetDateTime.now(), details);
    }
}
