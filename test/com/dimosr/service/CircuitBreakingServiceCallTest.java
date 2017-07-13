package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.OpenCircuitBreakerException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.util.function.Function;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CircuitBreakingServiceCallTest {

    @Mock
    private ServiceCall<String, String> rootServiceCall;
    private String serviceCallID = "service-id";

    @Mock
    private Clock clock;
    @Mock
    private MetricsCollector metricsCollector;

    private static final int REQUESTS_WINDOW = 10;
    private static final int FAILURES_OPEN_THRESHOLD = 3;
    private static final int SUCCESSES_CLOSE_THRESHOLD = 2;

    private static final int HALF_OPEN_DURATION_MILLISECONDS = 100;

    private CircuitBreakingServiceCall<String, String> serviceCall;

    private static final String VALID_REQUEST = "request";
    private static final String RESPONSE = "response";
    private static final String FAILING_REQUEST = "failing-request";

    private long clockTime = 0;

    private static final String METRIC_FOR_CLOSED = "ServiceCall.service-id.CircuitBreaker.state.CLOSED";
    private static final String METRIC_FOR_HALF_OPEN = "ServiceCall.service-id.CircuitBreaker.state.HALF_OPEN";
    private static final String METRIC_FOR_OPEN = "ServiceCall.service-id.CircuitBreaker.state.OPEN";

    private static final String CONTANT_RESPONSE = "constant-output";
    Function<String, String> responseSupplier = input -> CONTANT_RESPONSE;

    @Before
    public void setup() {
        setupCircuitBreakingServiceCallWithoutSupplier();
        setupRootService();
        setupClock();
    }

    private void setupCircuitBreakingServiceCallWithoutSupplier() {
        serviceCall = new CircuitBreakingServiceCall<>(rootServiceCall,
                serviceCallID,
                REQUESTS_WINDOW,
                FAILURES_OPEN_THRESHOLD,
                SUCCESSES_CLOSE_THRESHOLD,
                HALF_OPEN_DURATION_MILLISECONDS,
                clock,
                metricsCollector);
    }

    private void setupCircuitBreakingServiceCallWithSupplier(final Function<String, String> responseSupplier) {
        serviceCall = new CircuitBreakingServiceCall<>(rootServiceCall,
                responseSupplier,
                serviceCallID,
                REQUESTS_WINDOW,
                FAILURES_OPEN_THRESHOLD,
                SUCCESSES_CLOSE_THRESHOLD,
                HALF_OPEN_DURATION_MILLISECONDS,
                clock,
                metricsCollector);
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

        verify(metricsCollector, times(REQUESTS_WINDOW))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
    }

    @Test
    public void whenMoreRequestsThanThresholdAreFailingInsideWindowCircuitBreakerOpens_AndThrowsExceptionIfNoSupplierProvider() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);

        try {
            createSuccessfulRequests(1);
        } catch (OpenCircuitBreakerException e) {
            /* Nothing to do - verified that exception was thrown */
        }
        verify(metricsCollector, times(FAILURES_OPEN_THRESHOLD))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_OPEN), eq((double)1), any());
    }

    @Test
    public void whenMoreRequestsThanThresholdAreFailingInsideWindowCircuitBreakerOpens_AndReturnsSupplierValueWhenProvided() {
        setupCircuitBreakingServiceCallWithSupplier(responseSupplier);

        createFailingRequests(FAILURES_OPEN_THRESHOLD);

        String response = serviceCall.call(VALID_REQUEST);
        assertThat(response).isEqualTo(CONTANT_RESPONSE);

        verify(metricsCollector, times(FAILURES_OPEN_THRESHOLD))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_OPEN), eq((double)1), any());
    }

    @Test
    public void testFailedRequestsAreConsideredOnlyInsideTheWindowForOpeningCircuitBreaker() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD-1);
        createSuccessfulRequests(REQUESTS_WINDOW);
        createFailingRequests(1);
        createSuccessfulRequests(1);

        verify(metricsCollector, times(FAILURES_OPEN_THRESHOLD-1 + REQUESTS_WINDOW + 1 + 1))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
    }

    @Test
    public void whenInOpenStateAndIntervalElapsesCircuitTransitionsToHalfOpenState() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        advanceClockTime(HALF_OPEN_DURATION_MILLISECONDS+1);

        createSuccessfulRequests(1);

        verify(metricsCollector, times(FAILURES_OPEN_THRESHOLD))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
        verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_HALF_OPEN), eq((double)1), any());
    }

    @Test
    public void whenInHalfOpenStateIfNumberOfConsecutiveRequestsSucceedThenCircuitCloses() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        advanceClockTime(HALF_OPEN_DURATION_MILLISECONDS+1);

        createSuccessfulRequests(SUCCESSES_CLOSE_THRESHOLD);

        createFailingRequests(1);

        InOrder inOrder = inOrder(metricsCollector);
        inOrder.verify(metricsCollector, times(FAILURES_OPEN_THRESHOLD))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
        inOrder.verify(metricsCollector, times(SUCCESSES_CLOSE_THRESHOLD))
                .putMetric(eq(METRIC_FOR_HALF_OPEN), eq((double)1), any());
        inOrder.verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
    }

    @Test
    public void whenInHalfOpenStateIfNumberOfConsecutiveRequestsDontSucceedThenCircuitOpensAgain() {
        createFailingRequests(FAILURES_OPEN_THRESHOLD);
        advanceClockTime(HALF_OPEN_DURATION_MILLISECONDS+1);

        createSuccessfulRequests(SUCCESSES_CLOSE_THRESHOLD-1);
        createFailingRequests(1);

        try {
            createSuccessfulRequests(1);
            fail("Expected exception not thrown");
        } catch (OpenCircuitBreakerException e) {
            /* Nothing to do - verified that exception was thrown */
        }
        InOrder inOrder = inOrder(metricsCollector);
        inOrder.verify(metricsCollector, times(FAILURES_OPEN_THRESHOLD))
                .putMetric(eq(METRIC_FOR_CLOSED), eq((double)1), any());
        inOrder.verify(metricsCollector, times(SUCCESSES_CLOSE_THRESHOLD))
                .putMetric(eq(METRIC_FOR_HALF_OPEN), eq((double)1), any());
        inOrder.verify(metricsCollector)
                .putMetric(eq(METRIC_FOR_OPEN), eq((double)1), any());
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
