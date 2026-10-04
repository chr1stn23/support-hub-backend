package com.chrisdev.supporthub.common.util;

import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;

public final class Texts {

    private Texts() {
    }

    public static String requireText(String field, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "The field '%s' cannot be blank.".formatted(field));
        }
        return trimmed;
    }

    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Evita que % y _ escritos por el usuario actúen como comodines en un LIKE.
     */
    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
