package com.chrisdev.supporthub.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    TICKET_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    TICKET_NOT_ASSIGNABLE(HttpStatus.CONFLICT),
    TICKET_LOCKED(HttpStatus.CONFLICT),
    INVALID_ASSIGNEE(HttpStatus.BAD_REQUEST),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

    ASSET_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    ASSET_NOT_ASSIGNABLE(HttpStatus.CONFLICT),
    ASSET_NOT_ASSIGNED(HttpStatus.CONFLICT),
    ASSET_RETIRED(HttpStatus.CONFLICT),
    INVALID_ASSET_STATUS_TRANSITION(HttpStatus.CONFLICT),
    DUPLICATE_SERIAL_NUMBER(HttpStatus.CONFLICT),

    EMAIL_ALREADY_USED(HttpStatus.CONFLICT),
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT),
    LAST_ADMIN_REQUIRED(HttpStatus.CONFLICT),
    USER_HAS_ACTIVE_ASSIGNMENTS(HttpStatus.CONFLICT),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
