package com.deanmanagement.testmanagement.shared.exception;

/** The request is valid but clashes with the resource's current state (409), e.g. deleting something in use. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
