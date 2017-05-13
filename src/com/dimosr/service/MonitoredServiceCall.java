package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * A ServiceCall enhanced with monitoring capabilities.
 * The client must provide the necessary callbacks, which will be triggered with the values of the available metrics
 * Each metric is returned to the callback with the necessary timestamp of each call, so that the client can batch them, if needed
 *
 * For now, the monitoring capabilities include:
 * - latency of each call
 */
class MonitoredServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final Clock clock;
    private final BiConsumer<Instant, Duration> latencyConsumer;
    private ExecutorService executorService;

    /**
     * Constructs a monitored serviceCall, that will call the underlying serviceCall and provide the metrics through the callbacks
     * Note that the callbacks will be called synchronously, so it's not recommended for callbacks that are slow
     *
     * If any of the callbacks is slow, you can execute it asynchronously
     * To do that, use the other constructor which accepts an ExecutorService
     * @param serviceCall, the underlying serviceCall that will be called
     * @param clock, the clock used to measure latency
     * @param latencyConsumer, the callback that will be provided with the timestamp of the call and the calculated latency
     */
    public MonitoredServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final Clock clock,
                                final BiConsumer<Instant, Duration> latencyConsumer) {
        this.serviceCall = serviceCall;
        this.clock = clock;
        this.latencyConsumer = latencyConsumer;
    }

    /**
     * Constructs a monitored serviceCall, that will call the underlying serviceCall and provide the metrics through the callbacks
     * Note that the callbacks will be called asynchronously, using the provided ExecutorService
     *
     * @param serviceCall, the underlying serviceCall that will be called
     * @param clock, the clock used to measure latency
     * @param latencyConsumer, @param latencyConsumer, the callback that will be provided with the timestamp of the call and the calculated latency
     * @param executorService, the executorService that will be used to execute the callback
     */
    public MonitoredServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final Clock clock,
                                final BiConsumer<Instant, Duration> latencyConsumer,
                                final ExecutorService executorService) {
        this(serviceCall, clock, latencyConsumer);
        this.executorService = executorService;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Instant before = clock.instant();
        RESPONSE response = serviceCall.call(request);
        Instant after = clock.instant();

        Duration callDuration = Duration.between(before, after);
        executeLatencyCallback(before, callDuration);

        return response;
    }

    private void executeLatencyCallback(final Instant timestamp, final Duration callDuration) {
        if(executorService == null) {
            latencyConsumer.accept(timestamp, callDuration);
        } else {
            executorService.submit(() -> latencyConsumer.accept(timestamp, callDuration));
        }
    }
}
