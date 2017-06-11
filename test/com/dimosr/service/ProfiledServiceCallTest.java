package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProfiledServiceCallTest {

    @Mock
    private Clock mockClock;
    @Mock
    private ServiceCall<String, String> mockRootServiceCall;
    @Mock
    private MetricsCollector metricsCollector;

    private ServiceCall<String, String> profiledServiceCall;

    private static final Instant BEFORE_CALL = Instant.ofEpochMilli(1000);
    private static final long CALL_LATENCY_IN_MILLISECONDS = 500;
    private static final Instant AFTER_CALL = BEFORE_CALL.plusMillis(CALL_LATENCY_IN_MILLISECONDS);

    @Before
    public void setupMonitoredService() {
        profiledServiceCall = new ProfiledServiceCall<>(mockRootServiceCall, mockClock, metricsCollector);

        when(mockClock.instant())
                .thenReturn(BEFORE_CALL)
                .thenReturn(AFTER_CALL);
    }

    @Test
    public void whenMakingARequestLatencyIsCalculatedCorrectlyAndTriggersCallbackSync() {
        profiledServiceCall.call("irrelevant-request");

        verify(metricsCollector)
                .putMetric(eq("ServiceCall.latency"), eq((double)CALL_LATENCY_IN_MILLISECONDS), instantWithSameMilliseconds(BEFORE_CALL));
    }

    @Test
    public void whenMakingARequestReturnsResponseOfUnderlyingService() {
        final String request = "request";
        final String expectedResponse = "response";

        when(mockRootServiceCall.call(request))
                .thenReturn(expectedResponse);

        String actualResponse = profiledServiceCall.call(request);

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
