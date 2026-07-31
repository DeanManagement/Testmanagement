package com.deanmanagement.testmanagement.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void dataIntegrityViolationMapsTo409WithoutLeakingConstraintDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key uq_test_runs_key on table test_runs"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).doesNotContain("uq_test_runs_key");
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("FORBIDDEN");
    }

    @Test
    void unexpectedRuntimeExceptionMapsToGeneric500WithoutInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new NullPointerException("secret internal state"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret internal state");
    }

    @Test
    void statusCarryingRuntimeExceptionKeepsItsOwnStatus() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "upload too large"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().error()).isEqualTo("CONTENT_TOO_LARGE");
    }
}
