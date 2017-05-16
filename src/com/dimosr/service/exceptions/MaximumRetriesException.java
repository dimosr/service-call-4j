package com.dimosr.service.exceptions;

public class MaximumRetriesException extends RuntimeException {
    public MaximumRetriesException(final String message, final Throwable e) {
        super(message, e);
    }
}

