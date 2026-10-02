package com.chrisdev.supporthub.common.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s with id '%s' was not found.".formatted(resource, id));
    }
}
