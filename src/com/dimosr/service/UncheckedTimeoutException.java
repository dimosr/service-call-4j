package com.dimosr.service;

/**
 * The equivalent unchecked version of TimeoutException
 */
public class UncheckedTimeoutException extends RuntimeException {
    public UncheckedTimeoutException(final String message, final Throwable e) {
        super(message, e);
    }
}
