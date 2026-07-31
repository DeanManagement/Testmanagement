package com.deanmanagement.testmanagement.shared.exception;

/**
 * A call to an external service the tool depends on failed — the tracker was unreachable, rejected
 * our credentials, or returned something unusable. Surfaces as 502 so callers can tell "their
 * system is down" apart from "your request was wrong" (400) or "you may not do this" (403).
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
