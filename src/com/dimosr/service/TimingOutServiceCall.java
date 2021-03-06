package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.UncheckedTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class TimingOutServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private static final Logger log = LoggerFactory.getLogger(ThrottlingServiceCall.class);

    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final String serviceCallID;
    private final long timeout;
    private final TimeUnit accuracy;
    private final ExecutorService executorService;
    private final Clock clock;
    private final MetricsCollector metricsCollector;

    private static final String METRIC_TEMPLATE = "ServiceCall.%s.Timeouts";

    /**
     * A ServiceCall that will timeout after the provided time has passed
     * In case of timeout, an UncheckedTimeoutException will be thrown
     *
     * Note: TimeUnit.NANOSECONDS accuracy not supported, due to Java API deficiencies in conversion
     *
     * @param serviceCall the ServiceCall that will be executed
     * @param serviceCallID the ID under which the metric will be emitted
     * @param timeout the timeout imposed on the call
     * @param accuracy the accuracy that will be used for the provided timeout (e.g. milliseconds, microseconds etc.)
     * @param executorService the executorService that will be used for executing the ServiceCall
     * @param clock the clock used to measure the timestamps for the emitted metrics
     * @param metricsCollector the collector used to emit metrics for the number of timeouts
     */
    TimingOutServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                         final String serviceCallID,
                         final Duration timeout,
                         final TimeUnit accuracy,
                         final ExecutorService executorService,
                         final Clock clock,
                         final MetricsCollector metricsCollector) {
        this.serviceCall = serviceCall;
        this.serviceCallID = serviceCallID;
        this.timeout = getValueRelativeToUnit(timeout, accuracy);
        this.accuracy = accuracy;
        this.executorService = executorService;
        this.clock = clock;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Future<RESPONSE> responseFuture = executorService.submit(() -> serviceCall.call(request));

        try {
            RESPONSE response = responseFuture.get(timeout, accuracy);
            emitMetric(0);
            return response;
        } catch (TimeoutException e) {
            emitMetric(1);
            String message = String.format("Service call timed out after %d %s", timeout, accuracy);
            log.info("{}: call timed out after {} {}", serviceCallID, timeout, accuracy.toString());
            throw new UncheckedTimeoutException(message, e);
        } catch(ExecutionException e) {
            throw unwrapExecutionException(e);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

    }

    private long getValueRelativeToUnit(final Duration duration, final TimeUnit accuracy) {
        switch (accuracy) {
            case NANOSECONDS: return duration.toNanos();
            case MILLISECONDS: return duration.toMillis();
            case SECONDS: return duration.getSeconds();
            case MINUTES: return duration.toMinutes();
            case HOURS: return duration.toHours();
            case DAYS: return duration.toDays();
            default: throw new UnsupportedOperationException("TimeUnit.MICROSECONDS not supported, because conversion to this unit not available from Java");
        }
    }

    private void emitMetric(final int numberOfTimeouts) {
        final String metricName = String.format(METRIC_TEMPLATE, serviceCallID);
        metricsCollector.putMetric(metricName, numberOfTimeouts, clock.instant());
    }

    private RuntimeException unwrapExecutionException(final ExecutionException exception) {
        return Optional.ofNullable(exception.getCause())
                .map(causeException -> {
                    if (causeException instanceof RuntimeException) {
                        return (RuntimeException) causeException;
                    } else {
                        return new RuntimeException(exception.getMessage());
                    }
                })
                .orElse(new RuntimeException(exception.getMessage()));
    }
}
