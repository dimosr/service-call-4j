package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A builder used to enhance a ServiceCall with additional capabilities.
 * Currently, the available capabilities are the following:
 * - caching
 * - monitoring (latency)
 * - timeouts
 *
 * The capabilities are built on top of the provided ServiceCall.
 * The layering is such that there is no interference (or the least possible) between the various capabilities.
 *
 * For instance, below is the reasoning behind some of the decisions around layering:
 * - Caching is above monitoring, so that only actual calls to the service are monitored
 * - Monitoring is above timeouts, so that only the non timed-out calls are being monitored
 *
 *
 * The layering on top of the ServiceCall is the following:
 *
 * -------------------------------------------------
 * |                 Caching                       |
 * -------------------------------------------------
 * |                Monitoring                     |
 * -------------------------------------------------
 * |                 Timeout                       |
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

    private ExecutorService monitoringExecutor;
    private BiConsumer<Instant, Duration> latencyConsumer;

    private Duration timeout;
    private TimeUnit accuracy;
    private ExecutorService timeoutExecutor;

    public ServiceCallBuilder(final ServiceCall<REQUEST, RESPONSE> serviceCall) {
        this.enhancedServiceCall = serviceCall;
    }

    /**
     * Enabled caching, so that responses from the underlying service will be cached
     * @param cache, the cache that will be used to cache responses of the services
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withCache(Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Enables monitoring capabilities
     * @param latencyConsumer, a lambda function, which will be called back with the latency of each call
     *
     * Note: the callback will be executed synchronously.
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withMonitoring(final BiConsumer<Instant, Duration> latencyConsumer) {
        this.latencyConsumer = latencyConsumer;
        this.monitoringExecutor = null;
        return this;
    }

    /**
     * Enabled monitoring capabilities
     * @param latencyConsumer, a lambda function, which will be called back with the latency of each call
     * @param executorService, the executor that will be used to execute the provided callbacks
     *
     * Note: the callback will be executed asynchronously, using the provided ExecutorService
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withMonitoring(final BiConsumer<Instant, Duration> latencyConsumer, final ExecutorService executorService) {
        this.latencyConsumer = latencyConsumer;
        this.monitoringExecutor = executorService;
        return this;
    }

    /**
     * Enables timeout capabilities
     * @param timeout, the timeout for each call
     * @param accuracy, the accuracy used for measuring the timeout
     * @param executor, the executorService that will be used for the timeout functionality
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withTimeouts(final Duration timeout, final TimeUnit accuracy, final ExecutorService executor) {
        this.timeout = timeout;
        this.accuracy = accuracy;
        this.timeoutExecutor = executor;
        return this;
    }

    public ServiceCall<REQUEST, RESPONSE> build() {
        wrapWithTimeouts();
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
            if(monitoringExecutor != null) {
                enhancedServiceCall = new MonitoredServiceCall<>(enhancedServiceCall, Clock.systemUTC(), latencyConsumer, monitoringExecutor);
            } else {
                enhancedServiceCall = new MonitoredServiceCall<>(enhancedServiceCall, Clock.systemUTC(), latencyConsumer);
            }
        }
    }

    private void wrapWithTimeouts() {
        if(timeoutExecutor != null) {
            enhancedServiceCall = new TimingOutServiceCall<>(enhancedServiceCall, timeout, accuracy, timeoutExecutor);
        }
    }
}
