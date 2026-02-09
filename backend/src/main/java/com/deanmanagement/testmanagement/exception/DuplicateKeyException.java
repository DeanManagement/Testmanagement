package com.deanmanagement.testmanagement.exception;

public class DuplicateKeyException extends RuntimeException {

    public DuplicateKeyException(String field, String value) {
        super("Duplicate value '" + value + "' for field: " + field);
    }
}
