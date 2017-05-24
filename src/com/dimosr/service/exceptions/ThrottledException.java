package com.dimosr.service.exceptions;

/**
 * An exception denoting that an operation has been throttled
 */
public class ThrottledException extends RuntimeException {
    public ThrottledException(final String message) { super(message); }
}
