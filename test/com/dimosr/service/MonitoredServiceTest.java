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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MonitoredServiceTest {

    @Mock
    private Clock mockClock;
    @Mock
    private ServiceCall<String, String> mockRootService;
    @Mock
    private Consumer<Duration> latencyConsumer;
    @Mock
    private ExecutorService mockExecutor;

    private ServiceCall<String, String> monitoredService;
    private ServiceCall<String, String> monitoredServiceWithExecutor;

    @Before
    public void setupMonitoredService() {
        monitoredService = new MonitoredService<>(mockRootService, mockClock, latencyConsumer);
        monitoredServiceWithExecutor = new MonitoredService<>(mockRootService, mockClock, latencyConsumer, mockExecutor);
    }

    @Test
    public void whenMakingARequestLatencyIsCalculatedCorrectlyAndTriggersCallbackSync() {
        Instant beforeCall = Instant.ofEpochMilli(1000);
        long latencyInMilliseconds = 500;
        when(mockClock.instant())
                .thenReturn(beforeCall)
                .thenReturn(beforeCall.plusMillis(latencyInMilliseconds));

        monitoredService.call("irrelevant-request");

        verify(latencyConsumer)
                .accept(argThat(new SameDurationInMillis(Duration.ofMillis(latencyInMilliseconds))));
    }

    @Test
    public void whenMakingARequestLatencyIsCalculatedCorrectlyAndTriggersCallbackASync() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        Instant beforeCall = Instant.ofEpochMilli(1000);
        long latencyInMilliseconds = 500;
        when(mockClock.instant())
                .thenReturn(beforeCall)
                .thenReturn(beforeCall.plusMillis(latencyInMilliseconds));

        InOrder inOrder = inOrder(mockExecutor, latencyConsumer);

        monitoredServiceWithExecutor.call("irrelevant-request");
        inOrder.verify(mockExecutor)
                .submit(runnableCaptor.capture());
        inOrder.verify(latencyConsumer, never())
                .accept(any(Duration.class));

        runnableCaptor.getValue().run();
        inOrder.verify(latencyConsumer)
                .accept(argThat(new SameDurationInMillis(Duration.ofMillis(latencyInMilliseconds))));
    }

    @Test
    public void whenMakingARequestReturnsResponseOfUnderlyingService() {
        final String request = "request";
        final String expectedResponse = "response";

        when(mockClock.instant())
                .thenReturn(Instant.EPOCH);
        when(mockRootService.call(request))
                .thenReturn(expectedResponse);

        String actualResponse = monitoredService.call(request);

        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    private class SameDurationInMillis implements ArgumentMatcher<Duration> {
        private final Duration expectedDuration;

        SameDurationInMillis(final Duration duration) {
            this.expectedDuration = duration;
        }

        @Override
        public boolean matches(Duration duration) {
            return expectedDuration.toMillis() == duration.toMillis();
        }
    }
}
