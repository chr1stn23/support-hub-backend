package com.chrisdev.supporthub.audit.application;

import java.util.LinkedHashMap;
import java.util.Map;

public class AuditMetadata {

    private AuditMetadata() {
    }

    /**
     * Pares clave, valor, clave, valor... Los valores null se omiten.
     */
    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return map;
    }
}
