package com.dimosr.service.proxy;

import com.dimosr.service.Cache;
import com.dimosr.service.ServiceCall;

import java.util.Optional;

public class CachedService<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private ServiceCall<REQUEST, RESPONSE> service;
    private Cache<REQUEST, RESPONSE> cache;

    public CachedService(final ServiceCall<REQUEST, RESPONSE> service, final Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        this.service = service;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Optional<RESPONSE> value = cache.get(request);

        return value.orElseGet(
                () -> {
                    RESPONSE response = service.call(request);
                    cache.put(request, response);
                    return response;
                }
        );
    }
}
