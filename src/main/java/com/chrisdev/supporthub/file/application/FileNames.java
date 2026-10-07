package com.chrisdev.supporthub.file.application;

import java.util.Locale;

public final class FileNames {

    private static final int MAX_LENGTH = 200;

    private FileNames() {
    }

    /**
     * Deja solo el nombre (sin carpetas), sin caracteres de control ni peligrosos, y con largo acotado.
     */
    public static String sanitize(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").strip();
        name = name.replaceAll("^\\.+", "");
        if (name.isBlank()) {
            return "file";
        }
        if (name.length() > MAX_LENGTH) {
            String ext = extensionOf(name);
            String base = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length() - 1);
            int keep = MAX_LENGTH - (ext.isEmpty() ? 0 : ext.length() + 1);
            name = base.substring(0, Math.min(base.length(), keep)) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0 || dot == filename.length() - 1)
                ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
