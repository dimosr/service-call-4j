package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;

public class ServiceCallBuilder<REQUEST, RESPONSE> {
    private ServiceCall<REQUEST, RESPONSE> enhancedService;
    private Cache<REQUEST, RESPONSE> cache;

    public ServiceCallBuilder(ServiceCall<REQUEST, RESPONSE> service) {
        this.enhancedService = service;
    }

    public ServiceCallBuilder<REQUEST, RESPONSE> withCache(Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        return this;
    }

    public ServiceCall<REQUEST, RESPONSE> build() {
        if(cache != null) {
            enhancedService = new CachedService<>(enhancedService, cache);
        }

        return enhancedService;
    }
}
