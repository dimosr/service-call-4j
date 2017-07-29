package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * A Service enhanced with a cache, which stores responses for requests already made in the serviceCall
 */
class CachedServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private static final Logger log = LoggerFactory.getLogger(CachedServiceCall.class);

    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final String serviceCallID;
    private final Cache<REQUEST, RESPONSE> cache;

    CachedServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                      final String serviceCallID,
                      final Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        this.serviceCall = serviceCall;
        this.serviceCallID = serviceCallID;
    }

    /**
     * If the cache contains an entry for this request, the corresponding response is returned from cache
     * Otherwise, response is fetched from the serviceCall and placed in the cache.
     */
    @Override
    public RESPONSE call(REQUEST request) {
        Optional<RESPONSE> value = cache.get(request);
        emitLog(value.isPresent());

        return value.orElseGet(
                () -> {
                    RESPONSE response = serviceCall.call(request);
                    cache.put(request, response);
                    return response;
                }
        );
    }

    private void emitLog(final boolean valueInCache) {
        if(valueInCache) {
            log.info("{}: Response was fetched from cache, the service will not be called", serviceCallID);
        } else {
            log.info("{}: Response was not in cache, it will be fetched from the service", serviceCallID);
        }
    }

}
