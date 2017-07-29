package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.ThrottledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class ThrottlingServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private static final Logger log = LoggerFactory.getLogger(ThrottlingServiceCall.class);

    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private String serviceCallID;
    private final long maxRequestsPerSecond;
    private final Clock clock;

    private final AtomicLong requestsCounter;
    private volatile long currentSecondTimestamp;

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    private final MetricsCollector metricsCollector;

    private static final long SECOND_IN_MILLISECONDS = 1000;

    private static final String METRIC_TEMPLATE = "ServiceCall.%s.Throttling";

    ThrottlingServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                          final String serviceCallID,
                          final long maxRequestsPerSecond,
                          final Clock clock,
                          final MetricsCollector metricsCollector) {
        this.serviceCall = serviceCall;
        this.serviceCallID = serviceCallID;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.clock = clock;
        requestsCounter = new AtomicLong(0);
        currentSecondTimestamp = truncateToSecond(clock.millis());
        this.metricsCollector = metricsCollector;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        resetStateIfSecondPassed();
        checkIfThresholdCrossed();

        return serviceCall.call(request);
    }

    /**
     * This method resets the throttling counter if a second has passed
     *
     * It uses the write lock to reset the state
     * It also uses a double-checked locking as an optimisation.
     *
     * Note that the usage of double-checked locking is safe in this case,
     * since currentSecondTimestamp is volatile
     */
    private void resetStateIfSecondPassed() {
        long callTimestamp = clock.millis();
        if(differMoreThanASecond(currentSecondTimestamp, callTimestamp)) {
            writeLock.lock();
            try {
                if(differMoreThanASecond(currentSecondTimestamp, callTimestamp)) {
                    currentSecondTimestamp = truncateToSecond(callTimestamp);
                    requestsCounter.set(0);
                    log.debug("{}: Second elapsed, resetting request counter back to zero", serviceCallID);
                }
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * This method counts the current request in the sum used for throttling
     *
     * It uses an atomic counter for the sum of requests
     * It also uses the read lock to prevent interference with the instance, where
     * the second has passed and the atomic counter (and the timestamp holder) are updated.
     *
     * This method throws a ThrottledException in case the sum of requests exceed the provided threshold
     */
    private void checkIfThresholdCrossed() {
        readLock.lock();
        try {
            final long requestCounterValue = requestsCounter.incrementAndGet();
            log.info("{}: {} number of requests done in the current second, the throttling threshold is {}", serviceCallID, requestCounterValue, maxRequestsPerSecond);
            if(requestCounterValue > maxRequestsPerSecond) {
                emitMetric(1);
                throw new ThrottledException(String.format(
                        "Request was throttled, there were %s requests in the current second, while the threshold is: %s",
                        requestsCounter.get(),
                        maxRequestsPerSecond)
                );
            } else {
                emitMetric(0);
            }
        } finally {
            readLock.unlock();
        }
    }

    private boolean differMoreThanASecond(final long timestamp1, final long timestamp2) {
        return timestamp2 - timestamp1 > SECOND_IN_MILLISECONDS;
    }

    /**
     * Receives a timestamp with milliseconds precision and
     * returns the timestamp corresponding to the start of this second
     */
    private long truncateToSecond(long timestampInMilliseconds) {
        return (timestampInMilliseconds / 1000) * 1000;
    }

    private void emitMetric(final int throttledRequests) {
        final String metricName = String.format(METRIC_TEMPLATE, serviceCallID);
        metricsCollector.putMetric(metricName, throttledRequests, clock.instant());
    }
}
