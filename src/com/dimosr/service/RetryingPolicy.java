package com.dimosr.service;

import java.time.Duration;


class RetryingPolicy {

    private final Duration backoffInterval;

    private final BackoffPolicy backoffPolicy;
    /**
     * Enum containing different policies used for retrying ServiceCalls:

     */

    /**
     * This class contains the necessary information, defining the policy that will be used for retrying ServiceCalls
     *
     * @param backoffInterval, the interval that will be used as backoff, when retrying
     * @param backoffPolicy, the backoff policy that will be applied on the provided backoff interval
     *
     * The various backoff policies available are the following:
     * - ZERO_BACKOFF: retry will be performed directly after the failure, without any delay (the provided backoffInterval is ignored)
     * - CONSTANT_BACKOFF: retry will be performed, after constant delay provided by the client (constant delay will be equal to the provided backoffInterval)
     * - LINEAR_BACKOFF: retry will be performed, after a delay, linearly increasing by an increment provided by the client (equal to the backoffInterval)
     * - EXPONENTIAL_BACKOFF: retry will be performed after a delay, exponentially increasing by an increment provided by the client (equal to the backoffInterval)
     */
    public RetryingPolicy(final BackoffPolicy backoffPolicy, final Duration backoffInterval) {
        this.backoffInterval = backoffInterval;
        this.backoffPolicy = backoffPolicy;
    }

    /**
     * @param retry, a number indicating how many retries have already been performed (e.g. 1, if this is the first retry)
     * @return the duration of the delay that must be performed before applying this retry, according to the backoff policy used
     */
    public Duration getRetryBackoff(final int retry) {
        if(retry <= 0) {
            throw new IllegalArgumentException("The retry index has to be a positive number, but it was: " + retry);
        }

        switch (backoffPolicy) {
            case ZERO_BACKOFF:
                return Duration.ZERO;
            case CONSTANT_BACKOFF:
                return backoffInterval;
            case LINEAR_BACKOFF:
                Duration linearBackoff = backoffInterval.multipliedBy(retry);
                return linearBackoff;
            case EXPONENTIAL_BACKOFF:
                Duration exponentialBackoff = backoffInterval.multipliedBy((long) Math.pow(2, retry-1));
                return exponentialBackoff;
            default:
                throw new IllegalArgumentException("Invalid backoff policy: " + backoffPolicy);
        }
    }
}
