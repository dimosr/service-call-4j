package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.MaximumRetriesException;
import com.dimosr.service.exceptions.RetryableException;
import com.dimosr.service.util.Sleeper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A ServiceCall that will retry calls that throw specific exceptions
 * There client can define the RetryPolicy and the backoff interval, which will be used with milliseconds precision
 */
class RetryableServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final RetryingPolicy retryingPolicy;
    private final int maxRetries;

    private final Sleeper sleeper;

    private final List<Class> retryableExceptions = new ArrayList<>();
    {retryableExceptions.add(RetryableException.class);}

    /**
     * A ServiceCall that will be retried, when the underlying serviceCall throws a RetryableException
     *
     * @param serviceCall, the underlying serviceCall that will be called
     * @param retryingPolicy, the policy defining what the backoff of each retry will be
     * @param maxRetries, the number of maximum retries
     * @param sleeper, a component providing the utility of postponing the execution of the current thread for some period
     */
    public RetryableServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final RetryingPolicy retryingPolicy,
                                final int maxRetries,
                                final Sleeper sleeper) {
        this.serviceCall = serviceCall;
        this.retryingPolicy = retryingPolicy;
        this.maxRetries = maxRetries;
        this.sleeper = sleeper;
    }

    /**
     * A ServiceCall that will be retried, when the underlying serviceCall throws either a RetryableException
     * or one of the exceptions provided in the constructor
     *
     * @param serviceCall, the underlying serviceCall that will be called
     * @param retryingPolicy, the policy defining what the backoff of each retry will be
     * @param retryableExceptions, the list of exceptions that will be retried
     * @param sleeper, a component providing the utility of postponing the execution of the current thread for some period
     * @param maxRetries, the number of maximum retries
     */
    public RetryableServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final RetryingPolicy retryingPolicy,
                                final int maxRetries,
                                final Sleeper sleeper,
                                final List<Class<? extends Throwable>> retryableExceptions) {
        this(serviceCall, retryingPolicy, maxRetries, sleeper);
        this.retryableExceptions.addAll(retryableExceptions);
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Throwable finalException = null;
        int retriesMade = 0;

        while(retriesMade <= maxRetries) {
            try {
                return serviceCall.call(request);
            } catch(Throwable exception) {
                if(isRetryable(exception)) {
                    finalException = exception;
                    try {
                        Duration backoff = retryingPolicy.getRetryBackoff(retriesMade+1);
                        sleeper.sleep(backoff.toMillis());
                    } catch (InterruptedException e) {
                        throw new MaximumRetriesException("ServiceCall interrupted while retrying", e);
                    }
                    retriesMade++;
                } else {
                    throw exception;
                }
            }
        }

        String failureMessage = String.format("ServiceCall failed after %d retries", maxRetries);
        throw new MaximumRetriesException(failureMessage, finalException);
    }

    private boolean isRetryable(Throwable exception) {
        return retryableExceptions.contains(exception.getClass());
    }
}
