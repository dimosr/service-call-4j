package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.MaximumRetriesException;
import com.dimosr.service.exceptions.RetryableException;
import com.dimosr.service.util.Sleeper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RetryableServiceCallTest {

    private RetryableServiceCall<String, String> retryableServiceCall;

    @Mock
    private ServiceCall<String, String> underlyingMockServiceCall;
    @Mock
    private Sleeper mockSleeper;

    private static class CustomException extends RuntimeException{}

    private static final Duration BACKOFF_INTERVAL = Duration.ofMillis(1);
    private static final int RETRIES = 2;

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    @Before
    public void setupRetryableServiceCall() {
        retryableServiceCall = new RetryableServiceCall<>(
                underlyingMockServiceCall,
                BACKOFF_INTERVAL,
                RETRIES,
                mockSleeper,
                Arrays.asList(CustomException.class));
    }

    @Test
    public void whenUnderlyingServiceRespondsSuccessfullyThenNoRetryHappens() {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenReturn(RESPONSE);

        String response = retryableServiceCall.call(REQUEST);

        verify(underlyingMockServiceCall, times(1)).call(REQUEST);
        assertThat(response).isEqualTo(RESPONSE);
    }

    @Test
    public void whenUnderlyingServiceFailsWithDefaultRetryableExceptionThenRequestIsRetriedAndSucceeds() throws InterruptedException {
        testExceptionThrownFromUnderlyingService(RetryableException.class);
    }

    @Test
    public void whenUnderlyingServiceFailsWithCustomRetryableExceptionThenRequestIsRetriedAndSucceeds() throws InterruptedException {
        testExceptionThrownFromUnderlyingService(CustomException.class);
    }

    @Test(expected = MaximumRetriesException.class)
    public void whenUnderlyingServiceFailsWithRetryableExceptionManyTimesThenRequestEventuallyFails() throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(RetryableException.class);

        retryableServiceCall.call(REQUEST);

        verify(underlyingMockServiceCall, times(3)).call(REQUEST);
        verify(mockSleeper, times(3)).sleep(BACKOFF_INTERVAL.toMillis());
    }

    @Test(expected = NullPointerException.class)
    public void whenUnderlyingServiceFailsWithNonRetryableExceptionThenRequestFailsAndExceptionPropagates() {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(NullPointerException.class);

        retryableServiceCall.call(REQUEST);
    }

    @Test(expected = MaximumRetriesException.class)
    public void whenInterruptedWhileSleepingThenRetryingIsAborted() throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(RetryableException.class);
        doThrow(InterruptedException.class).when(mockSleeper).sleep(anyLong());

        retryableServiceCall.call(REQUEST);
    }

    private void testExceptionThrownFromUnderlyingService(final Class<? extends Throwable> exceptionThrownClass) throws InterruptedException {
        when(underlyingMockServiceCall.call(REQUEST))
                .thenThrow(exceptionThrownClass)
                .thenReturn(RESPONSE);

        String response = retryableServiceCall.call(REQUEST);

        verify(underlyingMockServiceCall, times(2)).call(REQUEST);
        verify(mockSleeper).sleep(BACKOFF_INTERVAL.toMillis());
        assertThat(response).isEqualTo(RESPONSE);
    }

}
