package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.exceptions.UncheckedTimeoutException;
import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TimingOutServiceCallTest {

    @Mock
    private ServiceCall<String, String> underlyingServiceCall;
    private String serviceCallID = "service-id";

    @Mock
    private ExecutorService executorService;
    @Mock
    private Future responseFuture;
    @Mock
    private Clock clock;
    @Mock
    private MetricsCollector metricsCollector;

    private ServiceCall<String, String> timingOutServiceCall;

    private static final TimeUnit ACCURACY = TimeUnit.MILLISECONDS;
    private static final Duration TIMEOUT= Duration.ofMillis(1);

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    private static final String METRIC_FOR_TIMEOUTS = "ServiceCall.service-id.Timeouts";

    @Before
    public void setupServiceCall() {
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, TIMEOUT, ACCURACY, executorService, clock, metricsCollector);

        when(executorService.submit(any(Callable.class)))
                .thenReturn(responseFuture);
    }

    @Test
    public void whenUnderlyingServiceRespondsThenResponseIsSuccessfullyReturned() throws InterruptedException, ExecutionException, TimeoutException {
        setupUnderlyingServiceRespondingSuccessfully();

        String response = timingOutServiceCall.call(REQUEST);

        assertThat(response).isEqualTo(RESPONSE);

        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_TIMEOUTS), eq(0.0d), any());
    }

    @Test
    public void whenUnderlyingCallTimeoutsThenExceptionIsThrown() throws InterruptedException, ExecutionException, TimeoutException {
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(TimeoutException.class);

        try {
            timingOutServiceCall.call(REQUEST);
            fail("Expected exception not thrown");
        } catch (UncheckedTimeoutException e) {
            /* Nothing to do - verified that exception was thrown */
        }
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_TIMEOUTS), eq(1.0d), any());
    }

    @Test(expected = NullPointerException.class)
    public void whenUnderlyingCallThrowsRuntimeExceptionThenOriginalExceptionThrown() throws InterruptedException, ExecutionException, TimeoutException {
        ExecutionException exception = new ExecutionException("message", new NullPointerException());
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(exception);

        timingOutServiceCall.call(REQUEST);
    }

    @Test
    public void whenUnderlyingCallThrowsNonRuntimeExceptionThenRuntimeExceptionWithOriginalMessageThrown() throws InterruptedException, ExecutionException, TimeoutException {
        ExecutionException exception = new ExecutionException("message", new Exception());
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(exception);

        try {
            timingOutServiceCall.call(REQUEST);
            fail("Expected exception was not thrown");
        } catch(RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("message");
        }
    }

    @Test
    public void whenExecutionExceptionWithoutCauseIsThrownThenRuntimeExceptionWithOriginalMessageThrown() throws InterruptedException, ExecutionException, TimeoutException {
        ExecutionException exception = mock(ExecutionException.class);
        when(exception.getMessage()).thenReturn("message");
        when(exception.getCause()).thenReturn(null);
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(exception);

        try {
            timingOutServiceCall.call(REQUEST);
            fail("Expected exception was not thrown");
        } catch(RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("message");
        }
    }

    @Test
    public void whenFutureIsInterruptedThenRuntimeExceptionIsThrownAndInterruptStatusSet() throws InterruptedException, ExecutionException, TimeoutException {
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(InterruptedException.class);
        try {
            timingOutServiceCall.call(REQUEST);

            fail("Expected exception not thrown");
        } catch (RuntimeException e) {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void timingOutServiceCannotWorkWithMicroseconds() {
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofNanos(10000), TimeUnit.MICROSECONDS, executorService, clock, metricsCollector);
    }

    @Test
    public void timingOutServiceCanWorkWithAllOtherTimeUnits() {
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofNanos(10000), TimeUnit.NANOSECONDS, executorService, clock, metricsCollector);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofMillis(10000), TimeUnit.MILLISECONDS, executorService, clock, metricsCollector);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofSeconds(10000), TimeUnit.SECONDS, executorService, clock, metricsCollector);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofMinutes(10000), TimeUnit.MINUTES, executorService, clock, metricsCollector);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofHours(10000), TimeUnit.HOURS, executorService, clock, metricsCollector);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, serviceCallID, Duration.ofDays(10000), TimeUnit.DAYS, executorService, clock, metricsCollector);
    }

    private void setupUnderlyingServiceRespondingSuccessfully() {
        when(underlyingServiceCall.call(REQUEST))
                .thenReturn(RESPONSE);
        when(executorService.submit(any(Callable.class)))
                .thenAnswer(invocation -> {
                    String result = ((Callable<String>) invocation.getArgument(0)).call();
                    return CompletableFuture.completedFuture(result);
                });
    }

}
