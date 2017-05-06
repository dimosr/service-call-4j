package com.dimosr.service;

public interface ServiceCall<REQUEST, RESPONSE> {
    RESPONSE call(REQUEST request);
}
