package com.chrisdev.supporthub.common.exception;

public class TooManyAttemptsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_ATTEMPTS, "Too many failed attempts. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

}
