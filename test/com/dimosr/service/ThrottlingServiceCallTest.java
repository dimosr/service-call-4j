package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.ThrottledException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ThrottlingServiceCallTest {

    private static final String METRIC_FOR_THROTTLING = "ServiceCall.Throttling";

    private static final int MAX_REQUESTS_PER_SECOND = 5;

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    private static final long CURRENT_SECOND = Instant.now().toEpochMilli();

    @Mock
    private ServiceCall<String, String> serviceCall;
    @Mock
    private Clock clock;

    private ServiceCall<String, String> throttlingServiceCall;

    @Mock
    private MetricsCollector metricsCollector;

    @Before
    public void setupThrottlingServiceCall() {
        throttlingServiceCall = new ThrottlingServiceCall<>(serviceCall, MAX_REQUESTS_PER_SECOND, clock, metricsCollector);

        setupServiceCall();
        setupClockToReturnSameSecond();
    }

    @Test
    public void whenRequestsInSecondLessThanThresholdThenNoThrottlingExceptionThrown() {
        for(int i = 1; i <= MAX_REQUESTS_PER_SECOND; i++) {
            String response = throttlingServiceCall.call(REQUEST);
            assertThat(response).isEqualTo(RESPONSE);
        }

        verify(metricsCollector, times(MAX_REQUESTS_PER_SECOND))
                .putMetric(eq(METRIC_FOR_THROTTLING), eq(0.0d), any());
    }

    @Test
    public void whenRequestsInSecondMoreThanThresholdThenThrottlingExceptionIsThrown() {
        try {
            for(int i = 1; i <= (MAX_REQUESTS_PER_SECOND+1); i++) {
                throttlingServiceCall.call(REQUEST);
            }
            fail("Expected exception not thrown");
        } catch(ThrottledException e) {
            /* Nothing to do - verified that exception is thrown */
        }

        verify(metricsCollector, times(MAX_REQUESTS_PER_SECOND))
                .putMetric(eq(METRIC_FOR_THROTTLING), eq(0.0d), any());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_THROTTLING), eq(1.0d), any());
    }

    @Test
    public void whenSecondPassesThenThrottlingCapacityIsReplenished() {
        setupClockToReturnNextSecondJustBeforeCapacityIsConsumed();

        for(int i = 1; i <= (MAX_REQUESTS_PER_SECOND+1); i++) {
            String response = throttlingServiceCall.call(REQUEST);
            assertThat(response).isEqualTo(RESPONSE);
        }

        verify(metricsCollector, times(MAX_REQUESTS_PER_SECOND+1))
                .putMetric(eq(METRIC_FOR_THROTTLING), eq(0.0d), any());
    }

    private void setupServiceCall() {
        when(serviceCall.call(REQUEST))
            .thenReturn(RESPONSE);
    }

    private void setupClockToReturnSameSecond() {
        when(clock.millis())
                .thenReturn(CURRENT_SECOND);
    }

    private void setupClockToReturnNextSecondJustBeforeCapacityIsConsumed() {
        when(clock.millis()).thenAnswer(new Answer<Long>() {
            private int requestCount = 0;

            @Override
            public Long answer(InvocationOnMock invocationOnMock) throws Throwable {
                requestCount++;
                if(requestCount > MAX_REQUESTS_PER_SECOND) {
                    long nextSecond = CURRENT_SECOND + 1000;
                    return nextSecond;
                }

                return CURRENT_SECOND;
            }
        });
    }
}
