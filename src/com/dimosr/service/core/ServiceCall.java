package com.dimosr.service.core;

/**
 * An interface abstracting a call to a service
 * Specifically destined to abstract remote procedure calls (RPCs)
 */
public interface ServiceCall<REQUEST, RESPONSE> {
    RESPONSE call(REQUEST request);
}
