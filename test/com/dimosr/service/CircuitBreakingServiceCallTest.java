package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.OpenCircuitBreakerException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.time.Clock;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CircuitBreakingServiceCallTest {

    @Mock
    private Clock clock;
    @Mock
    private ServiceCall<String, String> rootServiceCall;

    private static final int REQUESTS_WINDOW = 10;
    private static final int FAILURES_OPEN_THRESHOLD = 3;
    private static final int SUCCESSES_CLOSE_THRESHOLD = 2;

    private static final int HALF_OPEN_DURATION_MILLISECONDS = 100;

    private CircuitBreakingServiceCall<String, String> serviceCall;

    private static final String VALID_REQUEST = "request";
    private static final String RESPONSE = "response";
    private static final String FAILING_REQUEST = "failing-request";

    private long clockTime = 0;

    @Before
    public void setup() {
        serviceCall = new CircuitBreakingServiceCall<>(rootServiceCall,
                                                       REQUESTS_WINDOW,
                                                       FAILURES_OPEN_THRESHOLD,
                                                       SUCCESSES_CLOSE_THRESHOLD,
                                                       HALF_OPEN_DURATION_MILLISECONDS,
                                                       clock);
        setupRootService();
        setupClock();
    }

    private void setupRootService() {
        when(rootServiceCall.call(VALID_REQUEST))
                .thenReturn(RESPONSE);
        when(rootServiceCall.call(FAILING_REQUEST))
                .thenThrow(RuntimeException.class);
    }

    private void setupClock() {
        when(clock.millis())
                .thenAnswer(new Answer<Long>() {
                    @Override
                    public Long answer(InvocationOnMock invocationOnMock) throws Throwable {
                        return clockTime;
                    }
                });
    }

    private void advanceClockTime(final int millis) {
        this.clockTime += millis;
    }

    @Test
    public void whenAllRequestsAreSuccessfulCircuitBreakerRemainsClosed() {
        createSuccessfulRequests(REQUESTS_WINDOW);
    }

    @Test(expected = OpenCircuitBreakerException.class)
    public void whenMoreRequestsThanThresholdAreFailingInsideWindowCircuitBreakerOpens() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        createSuccessfulRequests(1);
    }

    @Test
    public void testFailedRequestsAreConsideredOnlyInsideTheWindowForOpeningCircuitBreaker() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD-1);
        createSuccessfulRequests(REQUESTS_WINDOW);
        createFailingRequests(1);
        createSuccessfulRequests(1);
    }

    @Test
    public void whenInOpenStateAndIntervalElapsesCircuitTransitionsToHalfOpenState() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        advanceClockTime(HALF_OPEN_DURATION_MILLISECONDS+1);

        createSuccessfulRequests(1);
    }

    @Test
    public void whenInHalfOpenStateIfNumberOfConsecutiveRequestsSucceedThenCircuitCloses() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        advanceClockTime(HALF_OPEN_DURATION_MILLISECONDS+1);

        createSuccessfulRequests(SUCCESSES_CLOSE_THRESHOLD);

        createFailingRequests(1);
    }

    @Test(expected = OpenCircuitBreakerException.class)
    public void whenInHalfOpenStateIfNumberOfConsecutiveRequestsDontSucceedThenCircuitOpensAgain() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        advanceClockTime(HALF_OPEN_DURATION_MILLISECONDS+1);

        createSuccessfulRequests(SUCCESSES_CLOSE_THRESHOLD-1);
        createFailingRequests(1);

        createSuccessfulRequests(1);
    }

    private void createSuccessfulRequests(final int requestsNumber) {
        for(int i = 0; i < requestsNumber; i++) {
            serviceCall.call(VALID_REQUEST);
        }
    }

    private void createFailingRequests(final int requestsNumber) {
        for (int i = 0; i < requestsNumber; i++) {
            try {
                serviceCall.call(FAILING_REQUEST);
            } catch (RuntimeException e) {
                /* Do nothing, triggering failures to check circuit breaker */
            }
        }
    }


}
