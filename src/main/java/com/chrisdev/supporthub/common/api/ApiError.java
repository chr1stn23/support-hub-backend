package com.chrisdev.supporthub.common.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors
) {

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, List.of());
    }

    public static ApiError of(int status, String code, String message, String path,
            List<FieldErrorDetail> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, path, fieldErrors);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
