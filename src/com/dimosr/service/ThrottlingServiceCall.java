package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.ThrottledException;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class ThrottlingServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final long maxRequestsPerSecond;
    private final Clock clock;

    private final AtomicLong requestsCounter;
    private volatile long currentSecondTimestamp;

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    private static final long SECOND_IN_MILLISECONDS = 1000;

    public ThrottlingServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                 final long maxRequestsPerSecond,
                                 final Clock clock) {
        this.serviceCall = serviceCall;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.clock = clock;
        requestsCounter = new AtomicLong(0);
        currentSecondTimestamp = truncateToSecond(clock.millis());
    }

    @Override
    public RESPONSE call(REQUEST request) {
        resetStateIfSecondPassed();
        countRequestInCurrentSecond();

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
    private void countRequestInCurrentSecond() {
        readLock.lock();
        try {
            if(requestsCounter.incrementAndGet() > maxRequestsPerSecond) {
                throw new ThrottledException(String.format(
                        "Request was throttled, there were %s requests in the current second, while the threshold is: %s",
                        requestsCounter.get(),
                        maxRequestsPerSecond)
                );
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
}
