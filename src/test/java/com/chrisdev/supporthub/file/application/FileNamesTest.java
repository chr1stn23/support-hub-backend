package com.chrisdev.supporthub.file.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileNamesTest {

    @Test
    void removesAnyDirectoryComponent() {
        assertThat(FileNames.sanitize("../../etc/passwd.png")).isEqualTo("passwd.png");
        assertThat(FileNames.sanitize("C:\\Users\\lucia\\foto.jpg")).isEqualTo("foto.jpg");
    }

    @Test
    void removesControlAndDangerousCharacters() {
        assertThat(FileNames.sanitize("a\u0000b.txt")).isEqualTo("a_b.txt");
        assertThat(FileNames.sanitize("we\"ird<name>.txt")).isEqualTo("we_ird_name_.txt");
        assertThat(FileNames.sanitize("línea1\nlínea2.txt")).isEqualTo("línea1_línea2.txt");
    }

    @Test
    void hiddenFilesAndEmptyNamesGetASafeFallback() {
        assertThat(FileNames.sanitize(".htaccess")).isEqualTo("htaccess");
        assertThat(FileNames.sanitize("..")).isEqualTo("file");
        assertThat(FileNames.sanitize("   ")).isEqualTo("file");
        assertThat(FileNames.sanitize(null)).isEqualTo("file");
    }

    @Test
    void keepsUnicodeNames() {
        assertThat(FileNames.sanitize("captura de pantalla é.png")).isEqualTo("captura de pantalla é.png");
    }

    @Test
    void longNamesAreTruncatedButKeepTheirExtension() {
        String sanitized = FileNames.sanitize("a".repeat(500) + ".png");
        assertThat(sanitized).hasSizeLessThanOrEqualTo(200).endsWith(".png");
    }

    @Test
    void extractsTheExtensionInLowerCase() {
        assertThat(FileNames.extensionOf("Foto.PNG")).isEqualTo("png");
        assertThat(FileNames.extensionOf("archivo.tar.gz")).isEqualTo("gz");
        assertThat(FileNames.extensionOf("sin-extension")).isEmpty();
        assertThat(FileNames.extensionOf("termina-en-punto.")).isEmpty();
    }
}
