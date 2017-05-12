package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServiceCallBuilderTest {

    @Mock
    private ServiceCall<String, String> originalServiceCall;

    private final static String SAMPLE_REQUEST = "input";
    private final static String SAMPLE_RESPONSE = "input with some more data";

    @Before
    public void setupOriginalServiceResponses() {
        when(originalServiceCall.call(SAMPLE_REQUEST))
                .thenReturn(SAMPLE_RESPONSE);
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
        Cache<String, String> cache = mock(Cache.class);
        when(cache.get(SAMPLE_REQUEST))
                .thenReturn(Optional.empty());


        ServiceCall<String, String> enhancedServiceCall = new ServiceCallBuilder<>(originalServiceCall)
                .withCache(cache)
                .build();

        String response = enhancedServiceCall.call(SAMPLE_REQUEST);

        InOrder orderSensitiveMocks = inOrder(originalServiceCall, cache);
        orderSensitiveMocks.verify(cache).get(SAMPLE_REQUEST);
        orderSensitiveMocks.verify(originalServiceCall).call(SAMPLE_REQUEST);
    }
}
