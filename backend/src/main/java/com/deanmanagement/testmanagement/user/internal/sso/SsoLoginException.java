package com.deanmanagement.testmanagement.user.internal.sso;

/**
 * A login that authenticated at the IdP but must not be granted a session here — typically because
 * linking it to an existing account would be unsafe (PRD-012 §4.1).
 *
 * <p>The message is shown to the person trying to log in, so it says what to do next without
 * revealing whether a given email has an account.
 */
public class SsoLoginException extends RuntimeException {

    public SsoLoginException(String message) {
        super(message);
    }
}
