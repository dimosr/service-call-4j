package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.MetricsCollector;
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
 * - profiling (latency)
 * - retrying
 * - timeouts
 * - throttling
 * - circuit breaker
 *
 * If monitoring is enabled, all enabled capabilities will also emit the relevant metrics
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
 * |                Profiling                      |
 * -------------------------------------------------
 * |                 Retrying                      |
 * -------------------------------------------------
 * |                 Timeout                       |
 * -------------------------------------------------
 * |                Throttling                     |
 * -------------------------------------------------
 * |              Circuit Breaker                  |
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

    private static final MetricsCollector NULL_METRICS_COLLECTOR = (namespace, value, timestamp) -> {};
    private MetricsCollector metricsCollector = NULL_METRICS_COLLECTOR;

    private RetryingPolicy retryingPolicy;
    private boolean retryTimeouts;
    private int maxRetries;

    private Duration timeout;
    private TimeUnit accuracy;
    private ExecutorService timeoutExecutor;

    private boolean isThrottlingEnabled = false;
    private long maxRequestsPerSecond;

    private boolean isCircuitBreakerEnabled = false;
    private int monitoredRequestsWindow;
    private int minimumFailingRequests;
    private int consecutiveSuccessfulRequests;
    private long durationOfOpenState;

    public ServiceCallBuilder(final ServiceCall<REQUEST, RESPONSE> serviceCall) {
        this.enhancedServiceCall = serviceCall;
    }

    /**
     * Enables caching, so that responses from the underlying service will be cached
     * @param cache the cache that will be used to cache responses of the services
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withCache(Cache<REQUEST, RESPONSE> cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Enables monitoring capabilities.
     * The provided metricsCollectors will be used to emit metrics around the operation of all the
     * enabled capabilities
     *
     * @param metricsCollector a collector used by all capabilities to emit metrics for their operations
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withMonitoring(final MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        return this;
    }

    /**
     * Enables retries, where each retry will be performed after a specific waiting-backoff period
     * @param backoffPolicy the backoff policy, which defines how the backoff increases for each retry
     * @param backoff the backoff period
     * @param retryTimeouts if timeouts will be retried
     * @param maxRetries the maximum number of retries that will be performed, before failing the request
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withRetrying(final BackoffPolicy backoffPolicy, final Duration backoff, final boolean retryTimeouts, final int maxRetries) {
        this.retryingPolicy = new RetryingPolicy(backoffPolicy, backoff);
        this.retryTimeouts = retryTimeouts;
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Enables retries, where retries will be performed directly without waiting-backoff period
     * @param retryTimeouts if timeouts will be retried
     * @param maxRetries the maximum number of retries that will be performed, before failing the request
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withRetrying(final boolean retryTimeouts, final int maxRetries) {
        withRetrying(BackoffPolicy.ZERO_BACKOFF, Duration.ZERO, retryTimeouts, maxRetries);
        return this;
    }

    /**
     * Enables timeout capabilities
     * @param timeout the timeout for each call
     * @param accuracy the accuracy used for measuring the timeout
     * @param executor the executorService that will be used for the timeout functionality
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

    /**
     * Enables throttling capabilities
     * @param maxRequestsPerSecond the maximum number of requests that will be dispatched to the service per second
     *
     * Note: the throttling functionality is thread-safe, so the serviceCall can be called by multiple threads
     *       and the throttling limit will be applied globally, based on the sum of calls of all the threads
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withThrottling(final long maxRequestsPerSecond) {
        this.isThrottlingEnabled = true;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        return this;
    }

    /**
     * Enabled circuit breaker capabilities
     * The circuit breaker can be in 3 states: CLOSED, HALF_OPEN, OPEN
     * - The initial state is CLOSED, where all the calls go through to the Service
     *   The latest {@code monitoredRequestsWindow} requests are being monitored and if at least
     *   {@code minimumFailingRequests} of them have failed, then the circuit breaker transitions
     *   to OPEN state
     * - When in OPEN state, the circuit breaker fails all requests with a {@code OpenCircuitBreakerException}
     *   without calling the underlying Service at all. The circuit breaker remains in OPEN state
     *   for {@code durationOfOpenState} milliseconds and then transitions to HALF_OPEN state
     * - When in HALF_OPEN state, the circuit breaker starts forwarding the requests to the
     *   underlying Service again. If the first {@code consecutiveSuccessfulRequests} are all
     *   successful, then the circuit breaker transitions to CLOSED state. Otherwise, the circuit
     *   breaker transitions to OPEN state.
     * @param monitoredRequestsWindow the number of the latest requests that are being monitored when in CLOSED state
     * @param minimumFailingRequests the number of requests, after which the circuit breaker transitions from CLOSED to OPEN state
     * @param consecutiveSuccessfulRequests the number of successful requests, after which the circuit breaker transitions from HALF_OPEN to CLOSED state
     * @param durationOfOpenState the duration of the interval, for which the circuit breaker remains in OPEN state, before
     *                            transitioning to HALF_OPEN state
     */
    public ServiceCallBuilder<REQUEST, RESPONSE> withCircuitBreaker(final int monitoredRequestsWindow,
                                                                    final int minimumFailingRequests,
                                                                    final int consecutiveSuccessfulRequests,
                                                                    final long durationOfOpenState) {
        this.isCircuitBreakerEnabled = true;
        this.monitoredRequestsWindow = monitoredRequestsWindow;
        this.minimumFailingRequests = minimumFailingRequests;
        this.consecutiveSuccessfulRequests = consecutiveSuccessfulRequests;
        this.durationOfOpenState = durationOfOpenState;
        return this;
    }

    public ServiceCall<REQUEST, RESPONSE> build() {
        wrapWithCircuitBreaker();
        wrapWithThrottling();
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
        enhancedServiceCall = new ProfiledServiceCall<>(enhancedServiceCall, Clock.systemUTC(), metricsCollector);
    }

    private void wrapWithRetrying() {
        if(retryingPolicy != null) {
            if(retryTimeouts) {
                enhancedServiceCall = new RetryableServiceCall<>(enhancedServiceCall, retryingPolicy, maxRetries, Thread::sleep, Collections.singletonList(UncheckedTimeoutException.class), metricsCollector, Clock.systemUTC());
            } else {
                enhancedServiceCall = new RetryableServiceCall<>(enhancedServiceCall, retryingPolicy, maxRetries, Thread::sleep, metricsCollector, Clock.systemUTC());
            }
        }
    }

    private void wrapWithTimeouts() {
        if(timeoutExecutor != null) {
            enhancedServiceCall = new TimingOutServiceCall<>(enhancedServiceCall, timeout, accuracy, timeoutExecutor, Clock.systemUTC(), metricsCollector);
        }
    }

    private void wrapWithThrottling() {
        if(isThrottlingEnabled) {
            enhancedServiceCall = new ThrottlingServiceCall<>(enhancedServiceCall, maxRequestsPerSecond, Clock.systemUTC(), metricsCollector);
        }
    }

    private void wrapWithCircuitBreaker() {
        if(isCircuitBreakerEnabled) {
            enhancedServiceCall = new CircuitBreakingServiceCall<>(enhancedServiceCall, monitoredRequestsWindow, minimumFailingRequests, consecutiveSuccessfulRequests, durationOfOpenState, Clock.systemUTC(), metricsCollector);
        }
    }
}
