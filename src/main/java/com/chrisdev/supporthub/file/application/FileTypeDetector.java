package com.chrisdev.supporthub.file.application;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Detecta el tipo real de un archivo por su contenido. Ignora por completo el nombre y el Content-Type del cliente.
 */
public final class FileTypeDetector {

    public static final int HEAD_SIZE = 8192;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private FileTypeDetector() {
    }

    public static Optional<DetectedFileType> detect(byte[] head) {
        if (startsWith(head, PNG_MAGIC)) return Optional.of(DetectedFileType.PNG);
        if (startsWith(head, JPEG_MAGIC)) return Optional.of(DetectedFileType.JPEG);
        if (startsWith(head, PDF_MAGIC)) return Optional.of(DetectedFileType.PDF);
        if (looksLikeUtf8Text(head)) return Optional.of(DetectedFileType.TEXT);
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Texto = sin bytes de control (salvo tab y saltos de línea) y UTF-8 válido.
     */
    private static boolean looksLikeUtf8Text(byte[] head) {
        if (head.length == 0) {
            return false;
        }
        for (byte b : head) {
            int v = b & 0xFF;
            if ((v < 0x20 && v != '\t' && v != '\n' && v != '\r') || v == 0x7F) {
                return false;   // un NUL u otro byte de control casi siempre delata un binario
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        // endOfInput = false: un carácter multibyte cortado al final del bloque leído no cuenta como error
        CoderResult result = decoder.decode(ByteBuffer.wrap(head), CharBuffer.allocate(head.length), false);
        return !result.isError();
    }
}
