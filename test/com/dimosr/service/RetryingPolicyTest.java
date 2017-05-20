package com.dimosr.service;

import org.junit.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class RetryingPolicyTest {

    private static final Duration BACKOFF_DURATION = Duration.ofMillis(100);

    @Test
    public void zeroBackoffPolicyAlwaysReturnsZeroBackoffNoMatterWhatBackoffIsProvided() {
        RetryingPolicy retryingPolicy = new RetryingPolicy(BackoffPolicy.ZERO_BACKOFF, BACKOFF_DURATION);

        assertThat(retryingPolicy.getRetryBackoff(1)).isEqualTo(Duration.ZERO);
        assertThat(retryingPolicy.getRetryBackoff(2)).isEqualTo(Duration.ZERO);
        assertThat(retryingPolicy.getRetryBackoff(10)).isEqualTo(Duration.ZERO);
    }

    @Test
    public void constantBackoffPolicyAlwaysReturnsSameBackoffNoMatterWhatBackoffIsProvided() {
        RetryingPolicy retryingPolicy = new RetryingPolicy(BackoffPolicy.CONSTANT_BACKOFF, BACKOFF_DURATION);

        assertThat(retryingPolicy.getRetryBackoff(1)).isEqualTo(BACKOFF_DURATION);
        assertThat(retryingPolicy.getRetryBackoff(2)).isEqualTo(BACKOFF_DURATION);
        assertThat(retryingPolicy.getRetryBackoff(10)).isEqualTo(BACKOFF_DURATION);
    }

    @Test
    public void linearBackoffPolicyReturnsLinearlyIncreasingBackoffNoMatterWhatBackoffIsProvided() {
        RetryingPolicy retryingPolicy = new RetryingPolicy(BackoffPolicy.LINEAR_BACKOFF, BACKOFF_DURATION);

        assertThat(retryingPolicy.getRetryBackoff(1)).isEqualTo(BACKOFF_DURATION);
        assertThat(retryingPolicy.getRetryBackoff(2)).isEqualTo(BACKOFF_DURATION.multipliedBy(2));
        assertThat(retryingPolicy.getRetryBackoff(3)).isEqualTo(BACKOFF_DURATION.multipliedBy(3));
        assertThat(retryingPolicy.getRetryBackoff(10)).isEqualTo(BACKOFF_DURATION.multipliedBy(10));
    }

    @Test
    public void exponentialBackoffPolicyReturnsExponentiallyIncreasingBackoffNoMatterWhatBackoffIsProvided() {
        RetryingPolicy retryingPolicy = new RetryingPolicy(BackoffPolicy.EXPONENTIAL_BACKOFF, BACKOFF_DURATION);

        assertThat(retryingPolicy.getRetryBackoff(1)).isEqualTo(BACKOFF_DURATION);
        assertThat(retryingPolicy.getRetryBackoff(2)).isEqualTo(BACKOFF_DURATION.multipliedBy(2));
        assertThat(retryingPolicy.getRetryBackoff(3)).isEqualTo(BACKOFF_DURATION.multipliedBy(2*2));
        assertThat(retryingPolicy.getRetryBackoff(10)).isEqualTo(BACKOFF_DURATION.multipliedBy((long)Math.pow(2, 9)));
    }

    @Test(expected = NullPointerException.class)
    public void incorrectlyInitialisedPolicyThrowsException() {
        RetryingPolicy retryingPolicy = new RetryingPolicy(null, BACKOFF_DURATION);

        retryingPolicy.getRetryBackoff(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void askingBackoffForInvalidRetryIndexThrowsException() {
        RetryingPolicy retryingPolicy = new RetryingPolicy(BackoffPolicy.ZERO_BACKOFF, BACKOFF_DURATION);

        retryingPolicy.getRetryBackoff(0);
    }

}
