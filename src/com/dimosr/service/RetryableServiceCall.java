package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.MaximumRetriesException;
import com.dimosr.service.exceptions.RetryableException;
import com.dimosr.service.util.Sleeper;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * A ServiceCall that will retry calls that throw specific exceptions
 * There client can define the RetryPolicy and the backoff interval, which will be used with milliseconds precision
 */
class RetryableServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private static final Logger log = LoggerFactory.getLogger(RetryableServiceCall.class);

    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final String serviceCallID;

    private final RetryingPolicy retryingPolicy;
    private final int maxRetries;

    private final Sleeper sleeper;

    private final MetricsCollector metricsCollector;
    private final Clock clock;

    private final List<Class> retryableExceptions = Lists.newArrayList(RetryableException.class);

    private final static String METRIC_TEMPLATE = "ServiceCall.%s.Retries";

    /**
     * A ServiceCall that will be retried, when the underlying serviceCall throws a RetryableException
     *
     * @param serviceCall the underlying serviceCall that will be called
     * @param serviceCallID the ID under which the metric will be emitted
     * @param retryingPolicy the policy defining what the backoff of each retry will be
     * @param maxRetries the number of maximum retries
     * @param sleeper a component providing the utility of postponing the execution of the current thread for some period
     * @param metricsCollector the collector used to emit metrics about the number of retries
     * @param clock the clock used to record the timestamps of the metrics
     */
    public RetryableServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final String serviceCallID,
                                final RetryingPolicy retryingPolicy,
                                final int maxRetries,
                                final Sleeper sleeper,
                                final MetricsCollector metricsCollector,
                                final Clock clock) {
        this.serviceCall = serviceCall;
        this.serviceCallID = serviceCallID;
        this.retryingPolicy = retryingPolicy;
        this.maxRetries = maxRetries;
        this.sleeper = sleeper;
        this.metricsCollector = metricsCollector;
        this.clock = clock;
    }

    /**
     * A ServiceCall that will be retried, when the underlying serviceCall throws either a RetryableException
     * or one of the exceptions provided in the constructor
     *
     * @param serviceCall, the underlying serviceCall that will be called
     * @param serviceCallID the ID under which the metric will be emitted
     * @param retryingPolicy, the policy defining what the backoff of each retry will be
     * @param retryableExceptions, the list of exceptions that will be retried
     * @param sleeper, a component providing the utility of postponing the execution of the current thread for some period
     * @param maxRetries, the number of maximum retries
     * @param metricsCollector the collector used to emit metrics about the number of retries
     * @param clock the clock used to record the timestamps of the metrics
     */
    public RetryableServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final String serviceCallID,
                                final RetryingPolicy retryingPolicy,
                                final int maxRetries,
                                final Sleeper sleeper,
                                final List<Class<? extends Throwable>> retryableExceptions,
                                final MetricsCollector metricsCollector,
                                final Clock clock) {
        this(serviceCall, serviceCallID, retryingPolicy, maxRetries, sleeper, metricsCollector, clock);
        this.retryableExceptions.addAll(retryableExceptions);
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Throwable finalException = null;
        int retriesMade = 0;

        while(retriesMade <= maxRetries) {
            try {
                RESPONSE response = serviceCall.call(request);
                emitMetrics(retriesMade);
                return response;
            } catch(Exception exception) {
                if(isRetryable(exception)) {
                    log.info("{}: Request failed with exception {}", serviceCallID, exception.toString());
                    finalException = exception;
                    try {
                        Duration backoff = retryingPolicy.getRetryBackoff(retriesMade+1);
                        sleeper.sleep(backoff.toMillis());
                        log.info("{}: Will retry the request after having waited for {} milliseconds", serviceCallID, backoff.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new MaximumRetriesException("ServiceCall interrupted while retrying", e);
                    }
                    log.info("{}: Total number of retries made so far: {}", serviceCallID, retriesMade);
                    retriesMade++;
                } else {
                    emitMetrics(retriesMade);
                    throw exception;
                }
            }
        }

        emitMetrics(maxRetries);
        String failureMessage = String.format("ServiceCall failed after %d retries", maxRetries);
        throw new MaximumRetriesException(failureMessage, finalException);
    }

    private boolean isRetryable(Throwable exception) {
        return retryableExceptions.contains(exception.getClass());
    }

    private void emitMetrics(final int numberOfRetries) {
        final String metricName = String.format(METRIC_TEMPLATE, serviceCallID);
        metricsCollector.putMetric(metricName, numberOfRetries, clock.instant());
    }
}
