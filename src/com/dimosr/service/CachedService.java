package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;

import java.util.Optional;

/**
 * A Service enhanced with a cache, which stores responses for requests already made in the service
 */
class CachedService<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> service;
    private final Cache<REQUEST, RESPONSE> cache;

    CachedService(final ServiceCall<REQUEST, RESPONSE> service, final Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        this.service = service;
    }

    /**
     * If the cache contains an entry for this request, the corresponding response is returned from cache
     * Otherwise, response is fetched from the service and placed in the cache.
     */
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
