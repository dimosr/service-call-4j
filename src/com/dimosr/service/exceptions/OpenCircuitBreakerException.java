package com.dimosr.service.exceptions;

public class OpenCircuitBreakerException extends RuntimeException {
    public OpenCircuitBreakerException(final String message) {
        super(message);
    }
}
