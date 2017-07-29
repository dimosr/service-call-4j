package com.dimosr.service;

import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.OpenCircuitBreakerException;
import com.dimosr.service.util.StatisticsQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.function.Function;

class CircuitBreakingServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakingServiceCall.class);

    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final String serviceCallID;

    private final CircuitBreaker circuitBreaker;
    private Function<REQUEST, RESPONSE> responseSupplier;

    private final MetricsCollector metricsCollector;
    private final Clock clock;

    private static final String METRIC_TEMPLATE = "ServiceCall.%s.CircuitBreaker.state.%s";

    /**
     * Returns a ServiceCall with circuit-breaking capabilities, where an OpenCircuitBreakerException will be thrown
     * while the circuit breaker is open.
     *
     * @param serviceCall, the service whose calls will be wrapped with the circuit breaker
     * @param serviceCallID the ID under which the metric will be emitted
     * @param requestsWindow, the number of the last requests that will be considered for failures
     * @param failingRequestsToOpen, the number of failed requests in the last "requestsWindow" requests,
     *                               after which the circuit breaker will be opened
     * @param consecutiveSuccessfulRequestsToClose, the number of consecutive successful requests that have to be made
     *                                              in the HALF-OPEN state for the circuit breaker to close
     * @param durationOfOpenInMilliseconds, the interval that the circuit breaker remains in OPEN state in milliseconds
     * @param clock, the clock that will be used to measure the intervals
     */
    CircuitBreakingServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                               final String serviceCallID,
                               final int requestsWindow,
                               final int failingRequestsToOpen,
                               final int consecutiveSuccessfulRequestsToClose,
                               final long durationOfOpenInMilliseconds,
                               final Clock clock,
                               final MetricsCollector metricsCollector) {
        this.serviceCall = serviceCall;
        this.serviceCallID = serviceCallID;
        this.circuitBreaker = new CircuitBreaker(
                serviceCallID,
                requestsWindow,
                failingRequestsToOpen,
                consecutiveSuccessfulRequestsToClose,
                durationOfOpenInMilliseconds,
                clock
        );
        this.metricsCollector = metricsCollector;
        this.clock = clock;
    }

    /**
     * Returns a ServiceCall with circuit-breaking capabilities, where the value provided by the given
     * supplier is returned while the circuit breaker is open
     */
    CircuitBreakingServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                               final Function<REQUEST, RESPONSE> responseSupplier,
                               final String serviceCallID,
                               final int requestsWindow,
                               final int failingRequestsToOpen,
                               final int consecutiveSuccessfulRequestsToClose,
                               final long durationOfOpenInMilliseconds,
                               final Clock clock,
                               final MetricsCollector metricsCollector) {
        this(serviceCall, serviceCallID, requestsWindow, failingRequestsToOpen, consecutiveSuccessfulRequestsToClose, durationOfOpenInMilliseconds, clock, metricsCollector);
        this.responseSupplier = responseSupplier;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        CircuitBreaker.CircuitBreakerState currentState = circuitBreaker.getState();
        emitMetrics(currentState);
        if(currentState == CircuitBreaker.CircuitBreakerState.OPEN) {
            if(responseSupplier == null) {
                log.info("{}: circuit breaker is open, so there will be no call to the service, the request will fail", serviceCallID);
                throw new OpenCircuitBreakerException("Circuit breaker is opened, request to underlying service was not made.");
            } else {
                RESPONSE response = responseSupplier.apply(request);
                log.info("{}: circuit breaker is open, so there will be no call to the service, the response provided by the supplier will be returned {}", serviceCallID, response.toString());
                return response;
            }
        }

        try {
            RESPONSE response = serviceCall.call(request);
            circuitBreaker.updateState(CircuitBreaker.RequestResult.SUCCESS);
            return response;
        } catch(Exception e) {
            circuitBreaker.updateState(CircuitBreaker.RequestResult.FAILURE);
            throw e;
        }
    }

    private void emitMetrics(final CircuitBreaker.CircuitBreakerState state) {
        final String metricName = String.format(METRIC_TEMPLATE, serviceCallID, state);
        metricsCollector.putMetric(metricName, 1, clock.instant());
    }

    private static class CircuitBreaker {
        private final String serviceCallID;

        private final Clock clock;
        private final StatisticsQueue<RequestResult> closedRequestQueue;
        private StatisticsQueue<RequestResult> halfOpenRequestQueue;

        private final int failingRequestsToOpen;
        private final int consecutiveSuccessfulRequestsToClose;
        private final long openDurationInMilliseconds;

        private volatile CircuitBreakerState state;

        private long lastOpenedTimestamp;

        private CircuitBreaker(final String serviceCallID,
                               final int requestsWindow,
                               final int failingRequestsToOpen,
                               final int consecutiveSuccessfulRequestsToClose,
                               final long openDurationInMilliseconds,
                               final Clock clock) {
            this.serviceCallID = serviceCallID;
            this.state = CircuitBreakerState.CLOSED;
            this.failingRequestsToOpen = failingRequestsToOpen;
            this.consecutiveSuccessfulRequestsToClose = consecutiveSuccessfulRequestsToClose;
            this.closedRequestQueue = new StatisticsQueue<>(requestsWindow);
            this.halfOpenRequestQueue = new StatisticsQueue<>(consecutiveSuccessfulRequestsToClose);
            this.openDurationInMilliseconds = openDurationInMilliseconds;
            this.clock = clock;
        }

        private CircuitBreakerState getState() {
            if (state == CircuitBreakerState.OPEN && (hasOpenDurationExpired())) {
                halfOpenCircuit();
            }
            return state;
        }

        private void updateState(final RequestResult requestResult) {
            synchronized (closedRequestQueue) {
                closedRequestQueue.addItem(requestResult);
                halfOpenRequestQueue.addItem(requestResult);

                if (state == CircuitBreakerState.HALF_OPEN) {
                    if(allSamplingRequestsCompleted()) {
                        if (allSamplingRequestsSuccessful()) {
                            log.info("{}: circuit breaker transitioned from HALF_OPEN to CLOSED", serviceCallID);
                            closeCircuit();
                        } else {
                            log.info("{}: circuit breaker transitioned from HALF_OPEN to OPEN", serviceCallID);
                            openCircuit();
                        }
                    }
                } else if (state == CircuitBreakerState.CLOSED && moreFailuresThanAcceptable()) {
                        log.info("{}: circuit breaker transitioned from CLOSED to OPEN", serviceCallID);
                        openCircuit();
                }
            }
        }

        private boolean hasOpenDurationExpired() {
            return clock.millis() - lastOpenedTimestamp > openDurationInMilliseconds;
        }

        private boolean moreFailuresThanAcceptable() {
            return closedRequestQueue.getOccurences(RequestResult.FAILURE) >= failingRequestsToOpen;
        }

        private boolean allSamplingRequestsCompleted() {
            return halfOpenRequestQueue.isFull();
        }

        private boolean allSamplingRequestsSuccessful() {
            return halfOpenRequestQueue.getOccurences(RequestResult.SUCCESS) >= consecutiveSuccessfulRequestsToClose;
        }

        private void openCircuit() {
            state = CircuitBreakerState.OPEN;
            lastOpenedTimestamp = clock.millis();
        }

        private void halfOpenCircuit() {
            state = CircuitBreakerState.HALF_OPEN;
            halfOpenRequestQueue.clear();
        }

        private void closeCircuit() {
            state = CircuitBreakerState.CLOSED;
            closedRequestQueue.clear();
        }

        private enum RequestResult {
            SUCCESS,
            FAILURE
        }

        private enum CircuitBreakerState {
            CLOSED,
            HALF_OPEN,
            OPEN
        }
    }
}
