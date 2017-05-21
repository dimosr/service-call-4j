package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.UncheckedTimeoutException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * A builder used to enhance a ServiceCall with additional capabilities.
 * Currently, the available capabilities are the following:
 * - caching
 * - monitoring (latency)
 * - retrying
 * - timeouts
 *
 * The capabilities are built on top of the provided ServiceCall.
 * The layering is such that there is no interference (or the least possible) between the various capabilities.
 *
 * For instance, below is the reasoning behind some of the decisions around layering:
 * - Caching is above monitoring, so that only actual calls to the service are monitored
 * - Monitoring is above retrying & timeouts, so that latency measured is for the total duration of the call (including any retries)
 * - Retrying is above timeouts, so that retrying can be configured to retry on timeouts (UncheckedTimeoutExceptions)
 *
 *
 * The layering on top of the ServiceCall is the following:
 *
 * -------------------------------------------------
 * |                 Caching                       |
 * -------------------------------------------------
 * |                Monitoring                     |
 * -------------------------------------------------
 * |                 Retrying                      |
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

    private RetryingPolicy retryingPolicy;
    private boolean retryTimeouts;
    private int maxRetries;

    private Duration timeout;
    private TimeUnit accuracy;
    private ExecutorService timeoutExecutor;

    public ServiceCallBuilder(final ServiceCall<REQUEST, RESPONSE> serviceCall) {
        this.enhancedServiceCall = serviceCall;
    }

    /**
     * Enables caching, so that responses from the underlying service will be cached
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
     * Enables monitoring capabilities
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
     * Enables retries, where each retry will be performed after a specific waiting-backoff period
     * @param backoffPolicy, the backoff policy, which defines how the backoff increases for each retry
     * @param backoff, the backoff period
     * @param retryTimeouts, if timeouts will be retried
     * @param maxRetries, the maximum number of retries that will be performed, before failing the request
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withRetrying(final BackoffPolicy backoffPolicy, final Duration backoff, final boolean retryTimeouts, final int maxRetries) {
        this.retryingPolicy = new RetryingPolicy(backoffPolicy, backoff);
        this.retryTimeouts = retryTimeouts;
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Enables retries, where retries will be performed directly without waiting-backoff period
     * @param retryTimeouts, if timeouts will be retried
     * @param maxRetries, the maximum number of retries that will be performed, before failing the request
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withRetrying(final boolean retryTimeouts, final int maxRetries) {
        withRetrying(BackoffPolicy.ZERO_BACKOFF, Duration.ZERO, retryTimeouts, maxRetries);
        return this;
    }

    /**
     * Enables timeout capabilities
     * @param timeout, the timeout for each call
     * @param accuracy, the accuracy used for measuring the timeout
     * @param executor, the executorService that will be used for the timeout functionality
     *
     * Attention: Timeout functionality makes use of future and multiple threads, thus is based on the assumption that the executor is multi-threaded
     *            So, if you provide a single-thread executor, note that the timeout functionality will not be enabled
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withTimeouts(final Duration timeout, final TimeUnit accuracy, final ExecutorService executor) {
        this.timeout = timeout;
        this.accuracy = accuracy;
        this.timeoutExecutor = executor;
        return this;
    }

    public ServiceCall<REQUEST, RESPONSE> build() {
        wrapWithTimeouts();
        wrapWithRetrying();
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

    private void wrapWithRetrying() {
        if(retryingPolicy != null) {
            if(retryTimeouts) {
                enhancedServiceCall = new RetryableServiceCall<>(enhancedServiceCall, retryingPolicy, maxRetries, Thread::sleep, Collections.singletonList(UncheckedTimeoutException.class));
            } else {
                enhancedServiceCall = new RetryableServiceCall<>(enhancedServiceCall, retryingPolicy, maxRetries, Thread::sleep);
            }
        }
    }

    private void wrapWithTimeouts() {
        if(timeoutExecutor != null) {
            enhancedServiceCall = new TimingOutServiceCall<>(enhancedServiceCall, timeout, accuracy, timeoutExecutor);
        }
    }
}
