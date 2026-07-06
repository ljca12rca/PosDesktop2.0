package com.posdesktop.pos.shared.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        boolean success,
        String message,
        String path,
        OffsetDateTime timestamp,
        T data
) {

    public static <T> ApiResponse<T> success(String message, String path, T data) {
        return new ApiResponse<>(true, message, path, OffsetDateTime.now(), data);
    }
}
