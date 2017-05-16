package com.dimosr.service.exceptions;

/**
 * An exception denoting that an operation can be potentially retried and succeed this time
 */
public class RetryableException extends RuntimeException {
    public RetryableException(final String message, final Throwable e) {
        super(message, e);
    }
}
