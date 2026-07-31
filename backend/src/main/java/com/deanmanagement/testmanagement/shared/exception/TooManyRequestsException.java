package com.deanmanagement.testmanagement.shared.exception;

/** Mapped to HTTP 429 by {@link GlobalExceptionHandler}. Used for login throttling (PRD-020). */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
