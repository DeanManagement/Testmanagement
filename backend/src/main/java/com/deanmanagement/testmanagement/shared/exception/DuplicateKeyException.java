package com.deanmanagement.testmanagement.shared.exception;

public class DuplicateKeyException extends RuntimeException {

    public DuplicateKeyException(String field, String value) {
        super("Duplicate value '" + value + "' for field: " + field);
    }
}
