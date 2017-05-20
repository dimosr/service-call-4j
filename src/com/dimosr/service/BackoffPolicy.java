package com.dimosr.service;

/**
 * The various backoff policies available are the following:
 * - ZERO_BACKOFF: retry will be performed directly after the failure, without any delay
 * - CONSTANT_BACKOFF: retry will be performed, after a constant backoff delay
 * - LINEAR_BACKOFF: retry will be performed, after a delay, which increases linearly by the backoff increment
 * - EXPONENTIAL_BACKOFF: retry will be performed after a delay, which increases exponentially by the backoff increment
 */
 public enum BackoffPolicy {
    ZERO_BACKOFF,
    CONSTANT_BACKOFF,
    LINEAR_BACKOFF,
    EXPONENTIAL_BACKOFF
}
