package com.ontheway.exception;

/**
 * Thrown when an authenticated caller is not permitted to act on a resource
 * they do not own. Maps to HTTP 403.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
