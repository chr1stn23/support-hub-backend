package com.chrisdev.supporthub.ticket.application;

import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import com.chrisdev.supporthub.common.exception.ResourceNotFoundException;
import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.file.application.*;
import com.chrisdev.supporthub.ticket.api.AttachmentResponse;
import com.chrisdev.supporthub.ticket.domain.Ticket;
import com.chrisdev.supporthub.ticket.domain.TicketAttachment;
import com.chrisdev.supporthub.ticket.infrastructure.TicketAttachmentRepository;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private final TicketLookup lookup;
    private final TicketAccessPolicy policy;
    private final TicketAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final FileStorage storage;
    private final StorageProperties props;
    private final AuditService audit;

    public AttachmentService(TicketLookup lookup, TicketAccessPolicy policy,
            TicketAttachmentRepository attachmentRepository, UserRepository userRepository,
            FileStorage storage, StorageProperties props, AuditService audit) {
        this.lookup = lookup;
        this.policy = policy;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.storage = storage;
        this.props = props;
        this.audit = audit;
    }

    // Subir
    @Transactional
    public AttachmentResponse upload(UUID ticketId, MultipartFile file, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);   // 404 si no existe o no lo ve
        if (ticket.isLocked()) {
            throw new BusinessException(ErrorCode.TICKET_LOCKED,
                    "A ticket in status %s no longer accepts attachments.".formatted(ticket.getStatus()));
        }
        User uploader = userRepository.findById(actor.id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "The authenticated user no longer exists or is inactive."));

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The file is empty.");
        }
        if (file.getSize() > props.maxFileSize().toBytes()) {   // el límite de multipart ya lo cubre; defensa extra
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "The file exceeds the maximum size of %s.".formatted(props.maxFileSize()));
        }
        if (attachmentRepository.countByTicketId(ticketId) >= props.maxFilesPerTicket()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT_REACHED,
                    "A ticket can have at most %d attachments.".formatted(props.maxFilesPerTicket()));
        }

        String displayName = FileNames.sanitize(file.getOriginalFilename());
        DetectedFileType type = detectAndValidate(file, displayName);

        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(in, type.storageExtension());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        deleteFileIfTransactionFails(stored.key());   // si la BD hace rollback, no queda un archivo huérfano

        TicketAttachment attachment = attachmentRepository.saveAndFlush(TicketAttachment.of(
                ticket, uploader, displayName, stored.key(), type.contentType(), stored.sizeBytes()));

        audit.record(actor.id(), AuditAction.ATTACHMENT_ADDED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(), "attachmentId", attachment.getId(),
                        "contentType", type.contentType(), "sizeBytes", stored.sizeBytes()));
        return AttachmentResponse.from(attachment);
    }

    // Listar
    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID ticketId, AuthenticatedUser actor) {
        lookup.requireVisible(ticketId, actor);
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    // Descargar
    @Transactional(readOnly = true)
    public AttachmentDownload download(UUID ticketId, UUID attachmentId, AuthenticatedUser actor) {
        lookup.requireVisible(ticketId, actor);
        TicketAttachment a = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        return new AttachmentDownload(a.getOriginalFilename(), a.getContentType(), a.getSizeBytes(),
                storage.open(a.getStorageKey()));
    }

    // Borrar
    @Transactional
    public void delete(UUID ticketId, UUID attachmentId, AuthenticatedUser actor) {
        Ticket ticket = lookup.requireVisible(ticketId, actor);
        TicketAttachment a = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        boolean uploader = a.getUploadedBy().getId().equals(actor.id());
        if (!uploader && !policy.hasFullControl(actor, ticket)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Only the uploader, the assigned agent or an admin can delete an attachment.");
        }
        if (ticket.isLocked()) {
            throw new BusinessException(ErrorCode.TICKET_LOCKED,
                    "A ticket in status %s no longer allows changes to its attachments.".formatted(ticket.getStatus()));
        }

        attachmentRepository.delete(a);
        attachmentRepository.flush();
        deleteFileAfterCommit(a.getStorageKey());   // primero la fila; el archivo, solo si la BD confirma

        audit.record(actor.id(), AuditAction.ATTACHMENT_DELETED, AuditEntityType.TICKET, ticket.getId(),
                AuditMetadata.of("ticketCode", ticket.getTicketCode(), "attachmentId", a.getId()));
    }

    // Helpers

    /**
     * El tipo real sale de los bytes; la extensión del nombre solo se contrasta con él.
     */
    private DetectedFileType detectAndValidate(MultipartFile file, String displayName) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(FileTypeDetector.HEAD_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        DetectedFileType type = FileTypeDetector.detect(head).orElseThrow(() ->
                new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "Unsupported file type. Allowed: PNG, JPEG, PDF and UTF-8 text files (.txt, .log)."));

        if (!type.acceptsExtension(FileNames.extensionOf(displayName))) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "The file extension does not match its content (%s).".formatted(type.contentType()));
        }
        return type;
    }

    private void deleteFileIfTransactionFails(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    private void deleteFileAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }
}