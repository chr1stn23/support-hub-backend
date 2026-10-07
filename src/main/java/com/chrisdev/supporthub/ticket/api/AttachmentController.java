package com.chrisdev.supporthub.ticket.api;

import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.ticket.application.AttachmentDownload;
import com.chrisdev.supporthub.ticket.application.AttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/attachments")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    // consumes: una petición que no sea multipart recibe un 415 limpio
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(@PathVariable UUID ticketId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        AttachmentResponse created = service.upload(ticketId, file, AuthenticatedUser.from(jwt));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}/download").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable UUID ticketId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(ticketId, AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID ticketId, @PathVariable UUID attachmentId,
            @AuthenticationPrincipal Jwt jwt) {
        AttachmentDownload file = service.download(ticketId, attachmentId, AuthenticatedUser.from(jwt));

        // attachment (nunca inline) + tipo detectado; ContentDisposition codifica y escapa el nombre
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .body(new InputStreamResource(file.stream()));
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ticketId, @PathVariable UUID attachmentId,
            @AuthenticationPrincipal Jwt jwt) {
        service.delete(ticketId, attachmentId, AuthenticatedUser.from(jwt));
    }
}
