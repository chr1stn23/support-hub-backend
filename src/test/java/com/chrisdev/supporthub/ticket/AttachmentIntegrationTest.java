package com.chrisdev.supporthub.ticket;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
public class AttachmentIntegrationTest extends IntegrationTestSupport {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Value("${app.storage.path}")
    String storagePath;

    private TestUser owner;

    @BeforeEach
    void setUp() {
        owner = createUser(RoleName.USER);
    }

    private ResultActions upload(TestUser user, String ticketId, String filename, String declaredType, byte[] content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, declaredType, content);
        return mvc.perform(multipart("/api/v1/tickets/{id}/attachments", ticketId).file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()));
    }

    private ResultActions uploadPng(TestUser user, String ticketId) throws Exception {
        return upload(user, ticketId, "pixel.png", "image/png", PNG);
    }

    @Test
    void aValidFileIsStoredAndDownloadedIntact() throws Exception {
        String ticketId = createTicket(owner, "Con adjunto");

        ResultActions uploaded = uploadPng(owner, ticketId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.filename").value("pixel.png"))
                .andExpect(jsonPath("$.sizeBytes").value(PNG.length));
        String attachmentId = read(uploaded, "$.id");

        byte[] downloaded = get(owner, "/api/v1/tickets/%s/attachments/%s/download".formatted(ticketId, attachmentId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PNG);
    }

    @Test
    void theRealTypeIsDetectedFromTheContentNotFromTheClient() throws Exception {
        String ticketId = createTicket(owner, "Disfraces");

        // Ejecutable de Windows que se presenta como PNG, tanto en el nombre como en el Content-Type
        upload(owner, ticketId, "foto.png", "image/png", new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00})
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        // Contenido PNG con extensión .pdf
        upload(owner, ticketId, "documento.pdf", "application/pdf", PNG).andExpect(status().isUnsupportedMediaType());
        // SVG con JavaScript: es texto válido, pero la extensión .svg no está permitida
        upload(owner, ticketId, "x.svg", "image/svg+xml",
                "<svg onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8)).andExpect(status().isUnsupportedMediaType());
        // Texto con bytes de control
        upload(owner, ticketId, "datos.txt", "text/plain", "abc\0def".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        // Vacío
        upload(owner, ticketId, "vacio.txt", "text/plain", new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void hostileFilenamesNeverReachTheFileSystem() throws Exception {
        String ticketId = createTicket(owner, "Path traversal");

        String attachmentId = read(upload(owner, ticketId, "../../etc/passwd.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("passwd.png")), "$.id");

        String key = jdbc.queryForObject("SELECT storage_key FROM ticket_attachments WHERE id = ?",
                String.class, UUID.fromString(attachmentId));
        assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f\\-]{36}\\.png");   // nombre generado, sin rastro del original
    }

    @Test
    void attachmentsFollowTheTicketVisibilityRules() throws Exception {
        String ticketId = createTicket(owner, "Privado");
        String otherTicketId = createTicket(owner, "Otro del mismo dueño");
        String attachmentId = read(uploadPng(owner, ticketId).andExpect(status().isCreated()), "$.id");
        TestUser stranger = createUser(RoleName.USER);

        get(createUser(RoleName.AGENT), "/api/v1/tickets/" + ticketId + "/attachments").andExpect(status().isOk());
        get(stranger, "/api/v1/tickets/" + ticketId + "/attachments").andExpect(status().isNotFound());
        get(stranger, "/api/v1/tickets/%s/attachments/%s/download".formatted(ticketId, attachmentId)).andExpect(status().isNotFound());
        uploadPng(stranger, ticketId).andExpect(status().isNotFound());

        // IDOR: el adjunto existe y el dueño ve ambos tickets, pero no pertenece al de la URL
        get(owner, "/api/v1/tickets/%s/attachments/%s/download".formatted(otherTicketId, attachmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingRemovesTheRowAndTheFile() throws Exception {
        String ticketId = createTicket(owner, "Borrado");
        String attachmentId = read(uploadPng(owner, ticketId).andExpect(status().isCreated()), "$.id");
        String key = jdbc.queryForObject("SELECT storage_key FROM ticket_attachments WHERE id = ?",
                String.class, UUID.fromString(attachmentId));
        Path file = Path.of(storagePath).resolve(key);
        assertThat(file).exists();

        // Un agente que no lo subió ni está asignado no puede borrarlo
        delete(createUser(RoleName.AGENT), "/api/v1/tickets/%s/attachments/%s".formatted(ticketId, attachmentId))
                .andExpect(status().isForbidden());

        delete(owner, "/api/v1/tickets/%s/attachments/%s".formatted(ticketId, attachmentId)).andExpect(status().isNoContent());
        assertThat(file).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_attachments WHERE id = ?", Long.class,
                UUID.fromString(attachmentId))).isZero();
        delete(owner, "/api/v1/tickets/%s/attachments/%s".formatted(ticketId, attachmentId)).andExpect(status().isNotFound());
    }

    @Test
    void lockedTicketsAndTheLimitPerTicketRejectNewFiles() throws Exception {
        String cancelled = createTicket(owner, "Cancelado");
        post(owner, "/api/v1/tickets/" + cancelled + "/status", "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
        uploadPng(owner, cancelled).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_LOCKED"));

        String full = createTicket(owner, "Lleno");
        for (int i = 0; i < 10; i++) {
            uploadPng(owner, full).andExpect(status().isCreated());
        }
        uploadPng(owner, full).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_LIMIT_REACHED"));
    }
}
