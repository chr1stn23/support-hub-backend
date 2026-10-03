package com.chrisdev.supporthub.common.exception;

public class InvalidCredentialsException extends BusinessException {
    
    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
    }
}
