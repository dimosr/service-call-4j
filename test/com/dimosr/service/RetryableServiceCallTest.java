package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.MaximumRetriesException;
import com.dimosr.service.exceptions.RetryableException;
import com.dimosr.service.util.Sleeper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RetryableServiceCallTest {

    private RetryableServiceCall<String, String> retryableServiceCall;
    private String serviceCallID = "service-id";

    @Mock
    private ServiceCall<String, String> underlyingMockServiceCall;
    @Mock
    private Sleeper mockSleeper;
    @Mock
    private RetryingPolicy retryingPolicy;
    @Mock
    private MetricsCollector metricsCollector;
    @Mock
    private Clock clock;

    private static final String METRIC_FOR_RETRIES = "ServiceCall.service-id.Retries";

    private static class CustomException extends RuntimeException{}

    private static final Duration POLICY_BACKOFF_INTERVAL = Duration.ofMillis(1);
    private static final int RETRIES = 2;

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    @Before
    public void setupRetryableServiceCall() {
        when(retryingPolicy.getRetryBackoff(anyInt()))
                .thenReturn(POLICY_BACKOFF_INTERVAL);

        retryableServiceCall = new RetryableServiceCall<>(
                underlyingMockServiceCall,
                serviceCallID,
                retryingPolicy,
                RETRIES,
                mockSleeper,
                Arrays.asList(CustomException.class),
                metricsCollector,
                clock);
    }

    @Test
    public void whenUnderlyingServiceRespondsSuccessfullyThenNoRetryHappens() throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenReturn(RESPONSE);

        String response = retryableServiceCall.call(REQUEST);

        verify(underlyingMockServiceCall, times(1)).call(REQUEST);
        verify(retryingPolicy, never()).getRetryBackoff(anyInt());
        verify(mockSleeper, never()).sleep(anyLong());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_RETRIES), eq(0.0d), any());

        assertThat(response).isEqualTo(RESPONSE);
    }

    @Test
    public void whenUnderlyingServiceFailsWithDefaultRetryableExceptionThenRequestIsRetriedAndSucceeds() throws InterruptedException {
        testRetryableExceptionThrownFromUnderlyingService(new RetryableException("retried for some reason", new RuntimeException()));
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_RETRIES), eq(1.0d), any());
    }

    @Test
    public void whenUnderlyingServiceFailsWithCustomRetryableExceptionThenRequestIsRetriedAndSucceeds() throws InterruptedException {
        testRetryableExceptionThrownFromUnderlyingService(new CustomException());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_RETRIES), eq(1.0d), any());
    }

    @Test
    public void whenUnderlyingServiceFailsWithRetryableExceptionManyTimesThenRequestEventuallyFails() throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(RetryableException.class);

        try {
            retryableServiceCall.call(REQUEST);
            fail("Expected exception not thrown");
        } catch(MaximumRetriesException e) {
            /* Nothing to do - verified that exception was thrown */
        }
        verify(underlyingMockServiceCall, times(3)).call(REQUEST);
        verify(mockSleeper, times(3)).sleep(POLICY_BACKOFF_INTERVAL.toMillis());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_RETRIES), eq(2.0d), any());
    }

    @Test
    public void whenUnderlyingServiceFailsWithNonRetryableExceptionThenRequestFailsAndExceptionPropagates() {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(NullPointerException.class);

        try {
            retryableServiceCall.call(REQUEST);
            fail("Expected exception not thrown");
        } catch(NullPointerException e) {
            /* Nothing to do - verified that exception was thrown */
        }

        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_RETRIES), eq(0.0d), any());
    }

    @Test
    public void whenInterruptedWhileSleepingThenRetryingIsAbortedAndInterruptStatusSet() throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(RetryableException.class);
        doThrow(InterruptedException.class).when(mockSleeper).sleep(anyLong());

        try {
            retryableServiceCall.call(REQUEST);

            fail("Expected exception was not thrown");
        } catch(MaximumRetriesException e) {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    private void testRetryableExceptionThrownFromUnderlyingService(final Throwable exceptionThrown) throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(exceptionThrown)
                .thenReturn(RESPONSE);

        String response = retryableServiceCall.call(REQUEST);

        verify(underlyingMockServiceCall, times(2)).call(REQUEST);
        verify(mockSleeper).sleep(POLICY_BACKOFF_INTERVAL.toMillis());
        verify(retryingPolicy).getRetryBackoff(1);
        assertThat(response).isEqualTo(RESPONSE);
    }

}
