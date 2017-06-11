package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * A ServiceCall enhanced with profiling capabilities.
 *
 * For now, the metrics emitted by this component include:
 * - latency of each call
 */
class ProfiledServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final Clock clock;
    private MetricsCollector metricsCollector;

    private static final String METRIC_TEMPLATE = "ServiceCall.latency";

    /**
     * Constructs a profiled serviceCall, that will call the underlying serviceCall and emit metrics around latency
     * using the provided metricsCollector
     *
     * @param serviceCall the underlying serviceCall that will be called
     * @param clock the clock used to measure latency
     * @param metricsCollector the collector used to emit the metrics
     */
    ProfiledServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                        final Clock clock,
                        final MetricsCollector metricsCollector) {
        this.serviceCall = serviceCall;
        this.clock = clock;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Instant before = clock.instant();
        RESPONSE response = serviceCall.call(request);
        Instant after = clock.instant();

        Duration callDuration = Duration.between(before, after);
        emitLatencyMetric(before, callDuration);

        return response;
    }

    private void emitLatencyMetric(final Instant timestamp, final Duration callDuration) {
        metricsCollector.putMetric(METRIC_TEMPLATE, callDuration.toMillis(), timestamp);
    }
}
