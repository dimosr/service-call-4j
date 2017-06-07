package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;
import com.dimosr.service.exceptions.OpenCircuitBreakerException;
import com.dimosr.service.util.StatisticsQueue;

import java.time.Clock;

class CircuitBreakingServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final CircuitBreaker circuitBreaker;

    /**
     * Returns a ServiceCall with circuit-breaking capabiliies
     * @param serviceCall, the service whose calls will be wrapped with the circuit breaker
     * @param requestsWindow, the number of the last requests that will be considered for failures
     * @param failingRequestsToOpen, the number of failed requests in the last "requestsWindow" requests,
     *                               after which the circuit breaker will be opened
     * @param consecutiveSuccessfulRequestsToClose, the number of consecutive successful requests that have to be made
     *                                              in the HALF-OPEN state for the circuit breaker to close
     * @param durationOfOpenInMilliseconds, the interval that the circuit breaker remains in OPEN state in milliseconds
     * @param clock, the clock that will be used to measure the intervals
     */
    CircuitBreakingServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                      final int requestsWindow,
                                      final int failingRequestsToOpen,
                                      final int consecutiveSuccessfulRequestsToClose,
                                      final long durationOfOpenInMilliseconds,
                                      final Clock clock) {
        this.serviceCall = serviceCall;
        this.circuitBreaker = new CircuitBreaker(requestsWindow, failingRequestsToOpen, consecutiveSuccessfulRequestsToClose, durationOfOpenInMilliseconds, clock);
    }

    @Override
    public RESPONSE call(REQUEST request) {
        if(circuitBreaker.getState() == CircuitBreaker.CircuitBreakerState.OPEN) {
            throw new OpenCircuitBreakerException("Circuit breaker is opened, request to underlying service was not made.");
        }

        try {
            RESPONSE response = serviceCall.call(request);
            circuitBreaker.updateState(CircuitBreaker.RequestResult.SUCCESS);
            return response;
        } catch(Throwable e) {
            circuitBreaker.updateState(CircuitBreaker.RequestResult.FAILURE);
            throw e;
        }
    }

    private static class CircuitBreaker {
        private final Clock clock;
        private final StatisticsQueue<RequestResult> closedRequestQueue;
        private StatisticsQueue<RequestResult> halfOpenRequestQueue;

        private final int failingRequestsToOpen;
        private final int consecutiveSuccessfulRequestsToClose;
        private final long openDurationInMilliseconds;

        private volatile CircuitBreakerState state;

        private long lastOpenedTimestamp;

        private CircuitBreaker(final int requestsWindow,
                               final int failingRequestsToOpen,
                               final int consecutiveSuccessfulRequestsToClose,
                               final long openDurationInMilliseconds,
                               final Clock clock) {
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
                    if(halfOpenRequestQueue.isFull()) {
                        if (halfOpenRequestQueue.getOccurences(RequestResult.SUCCESS) >= consecutiveSuccessfulRequestsToClose) {
                            closeCircuit();
                        } else {
                            openCircuit();
                        }
                    }
                } else if (state == CircuitBreakerState.CLOSED) {
                    if (closedRequestQueue.getOccurences(RequestResult.FAILURE) >= failingRequestsToOpen) {
                        openCircuit();
                    }
                }
            }
        }

        private boolean hasOpenDurationExpired() {
            return clock.millis() - lastOpenedTimestamp > openDurationInMilliseconds;
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
