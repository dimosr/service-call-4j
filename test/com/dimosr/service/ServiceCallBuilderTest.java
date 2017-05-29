package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

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
    @Mock
    private Cache<String, String> cache;
    @Mock
    private BiConsumer<Instant, Duration> latencyConsumer;
    @Mock
    private ExecutorService executor;

    private final static int MAX_RETRIES = 2;

    private final static Duration TIMEOUT_THRESHOLD = Duration.ofMillis(1);

    private final static long MAX_REQUESTS_PER_SECOND = 100;

    @Test
    public void buildPlainService() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                                                                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall);
    }

    @Test
    public void buildServiceWithThrottling() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingAndTimeouts() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsAndRetrying() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingRetryableTimeoutsAndRetrying() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(true, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsRetryingAndMonitoring() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withMonitoring(latencyConsumer)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                MonitoredServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsRetryingAndAsyncMonitoring() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withMonitoring(latencyConsumer, executor)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                MonitoredServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
        );
    }

    @Test
    public void buildServiceWithThrottlingTimeoutsRetryingMonitoringAndCaching() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withCache(cache)
                .withMonitoring(latencyConsumer)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withThrottling(MAX_REQUESTS_PER_SECOND)
                .withRetrying(false, MAX_RETRIES)
                .build();

        verifyLayersAreInCorrectOrder(enhancedServiceCall,
                CachedServiceCall.class,
                MonitoredServiceCall.class,
                RetryableServiceCall.class,
                TimingOutServiceCall.class,
                ThrottlingServiceCall.class
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
