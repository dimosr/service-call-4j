package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
/**
 * A ServiceCall enhanced with profiling capabilities.
 *
 * For now, the metrics emitted by this component include:
 * - latency of each call
 */
class ProfiledServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private static final Logger log = LoggerFactory.getLogger(ProfiledServiceCall.class);

    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final String serviceCallID;
    private final Clock clock;
    private MetricsCollector metricsCollector;

    private static final String METRIC_TEMPLATE = "ServiceCall.%s.latency";

    /**
     * Constructs a profiled serviceCall, that will call the underlying serviceCall and emit metrics around latency
     * using the provided metricsCollector
     *
     * @param serviceCall the underlying serviceCall that will be called
     * @param serviceCallID the ID under which the metric will be emitted
     * @param clock the clock used to measure latency
     * @param metricsCollector the collector used to emit the metrics
     */
    ProfiledServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                        final String serviceCallID,
                        final Clock clock,
                        final MetricsCollector metricsCollector) {
        this.serviceCall = serviceCall;
        this.serviceCallID = serviceCallID;
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
        emitLog(before, after, callDuration);

        return response;
    }

    private void emitLog(final Instant before, final Instant after, final Duration callDuration) {
        log.info("{}: Request made at {}, Response retrieved at {}, duration of call {}", serviceCallID, before, after, callDuration);
    }

    private void emitLatencyMetric(final Instant timestamp, final Duration callDuration) {
        final String metricName = String.format(METRIC_TEMPLATE, serviceCallID);
        metricsCollector.putMetric(metricName, callDuration.toMillis(), timestamp);
    }
}
