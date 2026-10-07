package com.chrisdev.supporthub.file.application;

import java.util.Set;

public enum DetectedFileType {

    PNG("image/png", "png", Set.of("png")),
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg")),
    PDF("application/pdf", "pdf", Set.of("pdf")),
    TEXT("text/plain", "txt", Set.of("txt", "log"));

    private final String contentType;
    private final String storageExtension;
    private final Set<String> acceptedExtensions;

    DetectedFileType(String contentType, String storageExtension, Set<String> acceptedExtensions) {
        this.contentType = contentType;
        this.storageExtension = storageExtension;
        this.acceptedExtensions = acceptedExtensions;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * Extensión con la que se guarda en disco.
     */
    public String storageExtension() {
        return storageExtension;
    }

    public boolean acceptsExtension(String extension) {
        return acceptedExtensions.contains(extension);
    }
}
