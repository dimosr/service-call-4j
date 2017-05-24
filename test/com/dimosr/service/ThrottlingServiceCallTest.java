package com.dimosr.service;

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
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ThrottlingServiceCallTest {

    private static final int MAX_REQUESTS_PER_SECOND = 5;

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    private static final long CURRENT_SECOND = Instant.now().toEpochMilli();

    @Mock
    private ServiceCall<String, String> serviceCall;
    @Mock
    private Clock clock;

    private ServiceCall<String, String> throttlingServiceCall;

    @Before
    public void setupThrottlingServiceCall() {
        throttlingServiceCall = new ThrottlingServiceCall<>(serviceCall, MAX_REQUESTS_PER_SECOND, clock);

        setupServiceCall();
        setupClockToReturnSameSecond();
    }

    @Test
    public void whenRequestsInSecondLessThanThresholdThenNoThrottlingExceptionThrown() {
        for(int i = 1; i <= MAX_REQUESTS_PER_SECOND; i++) {
            String response = throttlingServiceCall.call(REQUEST);
            assertThat(response).isEqualTo(RESPONSE);
        }
    }

    @Test(expected = ThrottledException.class)
    public void whenRequestsInSecondMoreThanThresholdThenThrottlingExceptionIsThrown() {
        for(int i = 1; i <= (MAX_REQUESTS_PER_SECOND+1); i++) {
            throttlingServiceCall.call(REQUEST);
        }
    }

    @Test
    public void whenSecondPassesThenThrottlingCapacityIsReplenished() {
        setupClockToReturnNextSecondJustBeforeCapacityIsConsumed();

        for(int i = 1; i <= (MAX_REQUESTS_PER_SECOND+1); i++) {
            String response = throttlingServiceCall.call(REQUEST);
            assertThat(response).isEqualTo(RESPONSE);
        }
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
