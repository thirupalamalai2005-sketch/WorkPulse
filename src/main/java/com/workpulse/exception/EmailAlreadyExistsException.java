package com.workpulse.exception;

/**
 * Exception thrown when attempting to register with an already registered email.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
