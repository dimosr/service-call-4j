package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A builder used to enhance a ServiceCall with additional capabilities.
 * Currently, the available capabilities are the following:
 * - caching
 * - monitoring (latency)
 *
 * The capabilities are built on top of the provided ServiceCall.
 * The layering is such that there is no interference between the various capabilities.
 * The layering on top of the ServiceCall is the following:
 *
 * -------------------------------------------------
 * |                 Caching                       |
 * -------------------------------------------------
 * |                Monitoring                     |
 * -------------------------------------------------
 * |                ServiceCall                    |
 * -------------------------------------------------
 *                      |
 *                      |
 *                   -------
 *                   Service
 */
public class ServiceCallBuilder<REQUEST, RESPONSE> {
    private ServiceCall<REQUEST, RESPONSE> enhancedServiceCall;

    private Cache<REQUEST, RESPONSE> cache;
    private BiConsumer<Instant, Duration> latencyConsumer;

    public ServiceCallBuilder(final ServiceCall<REQUEST, RESPONSE> serviceCall) {
        this.enhancedServiceCall = serviceCall;
    }

    public ServiceCallBuilder<REQUEST, RESPONSE> withCache(Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        return this;
    }

    public ServiceCallBuilder<REQUEST, RESPONSE> withMonitoring(final BiConsumer<Instant, Duration> latencyConsumer) {
        this.latencyConsumer = latencyConsumer;
        return this;
    }

    public ServiceCall<REQUEST, RESPONSE> build() {
        wrapWithMonitoring();
        wrapInCache();

        return enhancedServiceCall;
    }

    private void wrapInCache() {
        if(cache != null) {
            enhancedServiceCall = new CachedServiceCall<>(enhancedServiceCall, cache);
        }
    }

    private void wrapWithMonitoring() {
        if(latencyConsumer != null) {
            enhancedServiceCall = new MonitoredServiceCall<>(enhancedServiceCall, Clock.systemUTC(), latencyConsumer);
        }
    }
}
