package com.ontheway.exception;

/**
 * Thrown when a request conflicts with the current state of a resource,
 * e.g. registering with an email that is already taken. Maps to HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
