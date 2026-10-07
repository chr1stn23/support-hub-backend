package com.chrisdev.supporthub.file.application;

import java.io.InputStream;

public interface FileStorage {

    /**
     * Guarda el contenido bajo un nombre generado. La extensión la decide el sistema, nunca el usuario.
     */
    StoredFile store(InputStream content, String extension);

    InputStream open(String key);

    /**
     * Idempotente y silencioso.
     */
    void delete(String key);
}
