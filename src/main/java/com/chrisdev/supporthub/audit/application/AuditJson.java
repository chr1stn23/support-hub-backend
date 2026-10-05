package com.chrisdev.supporthub.audit.application;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

public class AuditJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AuditJson() {
    }

    static String write(Map<String, Object> metadata) {
        return (metadata == null || metadata.isEmpty()) ? null : MAPPER.writeValueAsString(metadata);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return (Map<String, Object>) MAPPER.readValue(json, Map.class);
        } catch (RuntimeException ex) {
            return null; // una fila ilegible no debe romper el listado completo
        }
    }
}
