package com.chrisdev.supporthub.file.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FileTypeDetectorTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void detectsPng() {
        assertThat(FileTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)))
                .contains(DetectedFileType.PNG);
    }

    @Test
    void detectsJpeg() {
        assertThat(FileTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10))).contains(DetectedFileType.JPEG);
    }

    @Test
    void detectsPdf() {
        assertThat(FileTypeDetector.detect(text("%PDF-1.7\n%%EOF"))).contains(DetectedFileType.PDF);
    }

    @Test
    void detectsPlainTextIncludingAccents() {
        assertThat(FileTypeDetector.detect(text("hola mundo\n"))).contains(DetectedFileType.TEXT);
        assertThat(FileTypeDetector.detect(text("conexión rechazada\r\n\tintento 2"))).contains(DetectedFileType.TEXT);
    }

    @Test
    void aMultibyteCharacterCutAtTheEndOfTheBlockIsNotAnError() {
        assertThat(FileTypeDetector.detect(bytes('a', 'b', 'c', 0xC3))).contains(DetectedFileType.TEXT);
    }

    @Test
    void rejectsExecutablesBinaryDataAndEmptyInput() {
        assertThat(FileTypeDetector.detect(bytes(0x4D, 0x5A, 0x90, 0x00, 0x03, 0x00))).isEmpty();   // cabecera de .exe
        assertThat(FileTypeDetector.detect(bytes(0x00, 0xFF, 0x10, 0x80))).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
    }

    @Test
    void rejectsTextWithControlBytesOrInvalidUtf8() {
        assertThat(FileTypeDetector.detect(text("abc\0def"))).isEmpty();
        assertThat(FileTypeDetector.detect(bytes('a', 0xC3, 0x28))).isEmpty();   // secuencia UTF-8 inválida
    }

    @Test
    void theExtensionMustMatchTheContent() {
        assertThat(DetectedFileType.PNG.acceptsExtension("png")).isTrue();
        assertThat(DetectedFileType.PNG.acceptsExtension("pdf")).isFalse();
        assertThat(DetectedFileType.JPEG.acceptsExtension("jpeg")).isTrue();
        assertThat(DetectedFileType.TEXT.acceptsExtension("log")).isTrue();
        // Un SVG es texto válido, pero su extensión no está permitida para ningún tipo
        assertThat(DetectedFileType.TEXT.acceptsExtension("svg")).isFalse();
        assertThat(DetectedFileType.TEXT.acceptsExtension("html")).isFalse();
    }
}
