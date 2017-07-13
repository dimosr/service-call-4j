package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.MetricsCollector;
import com.dimosr.service.core.ServiceCall;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These tests do not aim to verify functionality provided over the ServiceCall
 * They aim to verify that all the layers are built on top of each other successfully and in the correct order
 * (This is the only reason why Java reflection is used)
 *
 * There are separate unit tests for each different layer on the components that implement the corresponding functionality
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceCallBuilderTest {

    @Mock
    private ServiceCall<String, String> originalServiceCall;
    private String serviceCallId = "service-id";

    @Mock
    private Cache<String, String> cache;
    @Mock
    private MetricsCollector metricsCollector;
    @Mock
    private ExecutorService executor;

    private final static int MAX_RETRIES = 2;

    private final static Duration TIMEOUT_THRESHOLD = Duration.ofMillis(1);

    private final static long MAX_REQUESTS_PER_SECOND = 100;

    private final int CIRCUIT_BREAKER_MONITORING_WINDOW = 25;
    private final int MIN_FAILING_REQUESTS = 10;
    private final int CONSECUTIVE_SUCCESSFUL_REQUESTS = 5;
    private final long OPEN_CIRCUIT_DURATION = 500;

    @Test
    public void buildPlainService() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                                                                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall, ProfiledServiceCall.class);
    }

    @Test
    public void buildServiceWithThrottling() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                ProfiledServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingAndTimeouts() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                ProfiledServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsAndRetrying() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                ProfiledServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingRetryableTimeoutsAndRetrying() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(true, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                ProfiledServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsRetryingAndMonitoring() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withMonitoring(metricsCollector)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                ProfiledServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsRetryingMonitoringAndCaching() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withCache(cache)
                .withMonitoring(metricsCollector)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                CachedServiceCall.class,
                ProfiledServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithCircuitBreakerThrottlingTimeoutsRetryingMonitoringAndCaching() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall, serviceCallId)
                .withCircuitBreaker(CIRCUIT_BREAKER_MONITORING_WINDOW, MIN_FAILING_REQUESTS, CONSECUTIVE_SUCCESSFUL_REQUESTS, OPEN_CIRCUIT_DURATION)
                .withCache(cache)
                .withMonitoring(metricsCollector)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                CachedServiceCall.class,
                ProfiledServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class,
                CircuitBreakingServiceCall.class
        );
    }

    private void verifyLayersAreInCorrectOrder(final ServiceCall topLevelServiceCall, final Class<? extends ServiceCall>... intermediateClasses) throws NoSuchFieldException, IllegalAccessException {
        ServiceCall currentServiceCall = topLevelServiceCall;
        for(Class<? extends ServiceCall> clazz: intermediateClasses) {
            assertThat(currentServiceCall).isInstanceOf(clazz);
            currentServiceCall = getNextLayerServiceCall(currentServiceCall);
        }

        assertThat(currentServiceCall).isEqualTo(originalServiceCall);
    }

    private ServiceCall getNextLayerServiceCall(final ServiceCall serviceCall) throws IllegalAccessException, NoSuchFieldException {
        Field underlyingServiceCallField = serviceCall.getClass().getDeclaredField("serviceCall");
        underlyingServiceCallField.setAccessible(true);
        return (ServiceCall) underlyingServiceCallField.get(serviceCall);
    }

}
