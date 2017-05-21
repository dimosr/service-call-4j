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



    @Test
    public void builtServiceHasCorrectlyOrderedLayers() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withCache(cache)
                .withMonitoring(latencyConsumer)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withRetrying(false, MAX_RETRIES)
                .build();

        checkLayersAreInCorrectOrder(enhancedServiceCall);
    }

    @Test
    public void builtServiceWithMonitoringAsyncAndRetryableTimeoutsHasCorrectlyOrderedLayers() throws NoSuchFieldException, IllegalAccessException {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withCache(cache)
                .withMonitoring(latencyConsumer, executor)
                .withTimeouts(TIMEOUT_THRESHOLD, TimeUnit.MILLISECONDS, executor)
                .withRetrying(true, MAX_RETRIES)
                .build();

        checkLayersAreInCorrectOrder(enhancedServiceCall);
    }

    private void checkLayersAreInCorrectOrder(final ServiceCall topLevelServiceCall) throws NoSuchFieldException, IllegalAccessException {
        assertThat(topLevelServiceCall).isInstanceOf(CachedServiceCall.class);

        ServiceCall secondLayerServiceCall = getNextLayerServiceCall(topLevelServiceCall);
        assertThat(secondLayerServiceCall).isInstanceOf(MonitoredServiceCall.class);

        ServiceCall thirdLayerServiceCall = getNextLayerServiceCall(secondLayerServiceCall);
        assertThat(thirdLayerServiceCall).isInstanceOf(RetryableServiceCall.class);

        ServiceCall fourthLayerServiceCall = getNextLayerServiceCall(thirdLayerServiceCall);
        assertThat(fourthLayerServiceCall).isInstanceOf(TimingOutServiceCall.class);

        ServiceCall fifthLayerServiceCall = getNextLayerServiceCall(fourthLayerServiceCall);
        assertThat(fifthLayerServiceCall).isEqualTo(originalServiceCall);
    }

    private ServiceCall getNextLayerServiceCall(final ServiceCall serviceCall) throws IllegalAccessException, NoSuchFieldException {
        Field underlyingServiceCallField = serviceCall.getClass().getDeclaredField("serviceCall");
        underlyingServiceCallField.setAccessible(true);
        return (ServiceCall) underlyingServiceCallField.get(serviceCall);
    }

}
