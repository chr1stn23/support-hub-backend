package com.chrisdev.supporthub.common.exception;

import com.chrisdev.supporthub.audit.application.AuditMetadata;
import com.chrisdev.supporthub.audit.application.AuditService;
import com.chrisdev.supporthub.audit.domain.AuditAction;
import com.chrisdev.supporthub.audit.domain.AuditEntityType;
import com.chrisdev.supporthub.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;

    public GlobalExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
    }

    private void auditDenied(HttpServletRequest request) {
        UUID actorId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                actorId = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
                // token con subject raro: se audita sin actor
            }
        }
        auditService.recordFailure(actorId, AuditAction.ACCESS_DENIED, AuditEntityType.SECURITY, null,
                AuditMetadata.of("method", request.getMethod(), "path", request.getRequestURI()));
    }

    // Excepciones propias
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();
        if (code == ErrorCode.FORBIDDEN) {
            auditDenied(request);
        }
        ApiError body = ApiError.of(code.status().value(), code.name(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(401, ErrorCode.UNAUTHORIZED.name(),
                "Authentication is required or the token is invalid.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        auditDenied(request);
        ApiError body = ApiError.of(403, ErrorCode.FORBIDDEN.name(),
                "You do not have permission to perform this action.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(409, ErrorCode.CONCURRENT_MODIFICATION.name(),
                "The resource was modified by someone else. Reload it and try again.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        // El detalle puede contener datos internos: va al log, no al cliente
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        ApiError body = ApiError.of(409, ErrorCode.CONFLICT.name(),
                "The operation conflicts with existing data.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Último recurso
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        ApiError body = ApiError.of(500, ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // Excepciones de Spring MVC
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        List<ApiError.FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ApiError body = ApiError.of(400, ErrorCode.VALIDATION_ERROR.name(),
                "Validation failed.", path(request), fieldErrors);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {

        ApiError body = ApiError.of(400, ErrorCode.MALFORMED_REQUEST.name(),
                "Malformed or unreadable request body.", path(request));
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    // Red de seguridad para el resto (404 de ruta, 405, 415, etc.)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, Object body, @NonNull HttpHeaders headers,
            HttpStatusCode statusCode, @NonNull WebRequest request) {

        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ApiError error = ApiError.of(status.value(), status.name(),
                status.getReasonPhrase(), path(request));
        return super.handleExceptionInternal(ex, error, headers, statusCode, request);
    }
}
