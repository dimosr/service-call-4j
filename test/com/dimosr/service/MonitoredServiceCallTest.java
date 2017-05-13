package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MonitoredServiceCallTest {

    @Mock
    private Clock mockClock;
    @Mock
    private ServiceCall<String, String> mockRootServiceCall;
    @Mock
    private BiConsumer<Instant, Duration> latencyConsumer;
    @Mock
    private ExecutorService mockExecutor;

    private ServiceCall<String, String> monitoredServiceCall;
    private ServiceCall<String, String> monitoredServiceWithExecutor;

    private static final Instant BEFORE_CALL = Instant.ofEpochMilli(1000);
    private static final long CALL_LATENCY_IN_MILLISECONDS = 500;
    private static final Instant AFTER_CALL = BEFORE_CALL.plusMillis(CALL_LATENCY_IN_MILLISECONDS);

    @Before
    public void setupMonitoredService() {
        monitoredServiceCall = new MonitoredServiceCall<>(mockRootServiceCall, mockClock, latencyConsumer);
        monitoredServiceWithExecutor = new MonitoredServiceCall<>(mockRootServiceCall, mockClock, latencyConsumer, mockExecutor);

        when(mockClock.instant())
                .thenReturn(BEFORE_CALL)
                .thenReturn(AFTER_CALL);
    }

    @Test
    public void whenMakingARequestLatencyIsCalculatedCorrectlyAndTriggersCallbackSync() {
        monitoredServiceCall.call("irrelevant-request");

        verify(latencyConsumer)
                .accept(instantWithSameMilliseconds(BEFORE_CALL), durationWithValue(CALL_LATENCY_IN_MILLISECONDS));
    }

    @Test
    public void whenMakingARequestLatencyIsCalculatedCorrectlyAndTriggersCallbackASync() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        InOrder inOrder = inOrder(mockExecutor, latencyConsumer);

        monitoredServiceWithExecutor.call("irrelevant-request");
        inOrder.verify(mockExecutor)
                .submit(runnableCaptor.capture());
        verify(latencyConsumer, never())
                .accept(any(Instant.class), any(Duration.class));

        runnableCaptor.getValue().run();
        inOrder.verify(latencyConsumer)
                .accept(instantWithSameMilliseconds(BEFORE_CALL), durationWithValue(CALL_LATENCY_IN_MILLISECONDS));
    }

    @Test
    public void whenMakingARequestReturnsResponseOfUnderlyingService() {
        final String request = "request";
        final String expectedResponse = "response";

        when(mockRootServiceCall.call(request))
                .thenReturn(expectedResponse);

        String actualResponse = monitoredServiceCall.call(request);

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    private static class SameDurationInMillis implements ArgumentMatcher<Duration> {
        private final Duration expectedDuration;

        private SameDurationInMillis(final Duration duration) {
            this.expectedDuration = duration;
        }

        @Override
        public boolean matches(Duration duration) {
            return expectedDuration.toMillis() == duration.toMillis();
        }
    }

    public static Duration durationWithValue(final long milliseconds) {
        return argThat(new SameDurationInMillis(Duration.ofMillis(milliseconds)));
    }

    private static class SameInstantInMillis implements ArgumentMatcher<Instant> {
        private final Instant expectedInstant;

        private SameInstantInMillis(final Instant instant) {
            this.expectedInstant = instant;
        }

        @Override
        public boolean matches(Instant instant) {
            return instant.toEpochMilli() == instant.toEpochMilli();
        }
    }

    public static Instant instantWithSameMilliseconds(final Instant instant) {
        return argThat(new SameInstantInMillis(instant));
    }
}
