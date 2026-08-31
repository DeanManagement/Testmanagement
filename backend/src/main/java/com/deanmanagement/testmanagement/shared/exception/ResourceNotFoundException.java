package com.deanmanagement.testmanagement.shared.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id: " + id);
    }

    /**
     * For a miss that spans several ids, where naming only the first would leave the caller
     * guessing how many more there are (PRD-027 §3.5).
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
