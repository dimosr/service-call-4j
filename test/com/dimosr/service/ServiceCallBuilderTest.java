package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServiceCallBuilderTest {

    @Mock
    private ServiceCall<String, String> originalServiceCall;
    @Mock
    private Cache<String, String> cache;
    @Mock
    private BiConsumer<Instant, Duration> latencyConsumer;

    private final static String SAMPLE_REQUEST = "input";
    private final static String SAMPLE_RESPONSE = "input with some more data";

    @Before
    public void setupOriginalServiceResponses() {
        when(originalServiceCall.call(SAMPLE_REQUEST))
                .thenReturn(SAMPLE_RESPONSE);
        when(cache.get(SAMPLE_REQUEST))
                .thenReturn(Optional.empty());
    }

    @Test
    public void testBuildingPlainServiceCall() {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                                                                                        .build();

        String response = enhancedServiceCall.call(SAMPLE_REQUEST);
        verify(originalServiceCall).call(SAMPLE_REQUEST);
    }

    @Test
    public void testBuildingServiceWithCache() {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withCache(cache)
                .build();

        enhancedServiceCall.call(SAMPLE_REQUEST);

        InOrder orderSensitiveMocks = inOrder(originalServiceCall, cache);
        orderSensitiveMocks.verify(cache).get(SAMPLE_REQUEST);
        orderSensitiveMocks.verify(originalServiceCall).call(SAMPLE_REQUEST);
    }

    @Test
    public void testBuildingServiceWithCacheAndMonitoring() {
        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withCache(cache)
                .withMonitoring(latencyConsumer)
                .build();

        enhancedServiceCall.call(SAMPLE_REQUEST);

        InOrder orderSensitiveMocks = inOrder(originalServiceCall, cache, latencyConsumer);
        orderSensitiveMocks.verify(cache).get(SAMPLE_REQUEST);
        orderSensitiveMocks.verify(originalServiceCall).call(SAMPLE_REQUEST);
        orderSensitiveMocks.verify(latencyConsumer).accept(any(Instant.class), any(Duration.class));
    }
}
