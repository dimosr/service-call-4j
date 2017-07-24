package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;

import java.util.Optional;

/**
 * A Service enhanced with a cache, which stores responses for requests already made in the serviceCall
 */
class CachedServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final Cache<REQUEST, RESPONSE> cache;

    CachedServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall, final Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        this.serviceCall = serviceCall;
    }

    /**
     * If the cache contains an entry for this request, the corresponding response is returned from cache
     * Otherwise, response is fetched from the serviceCall and placed in the cache.
     */
    @Override
    public RESPONSE call(REQUEST request) {
        Optional<RESPONSE> value = cache.get(request);

        return value.orElseGet(
                () -> {
                    RESPONSE response = serviceCall.call(request);
                    cache.put(request, response);
                    return response;
                }
        );
    }

}
