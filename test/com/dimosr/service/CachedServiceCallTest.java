package com.dimosr.service;

import com.dimosr.service.core.Cache;
import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class CachedServiceCallTest {

    private CachedServiceCall<String, String> cachedServiceCall;

    @Mock
    private ServiceCall<String, String> mockRootServiceCall;
    @Mock
    private Cache<String, String> mockCache;

    private static final String SAMPLE_REQUEST = "request";
    private static final String SAMPLE_RESPONSE = "response";

    @Before
    public void setupCachedService() {
        this.cachedServiceCall = new CachedServiceCall<>(mockRootServiceCall, mockCache);
    }

    @Test
    public void whenAskingUncachedRequestResponseIsReturnedFromServiceAndAlsoCached() {
        when(mockCache.get(SAMPLE_REQUEST))
                .thenReturn(Optional.empty());
        when(mockRootServiceCall.call(SAMPLE_REQUEST))
                .thenReturn(SAMPLE_RESPONSE);

        String response = cachedServiceCall.call(SAMPLE_REQUEST);

        verify(mockCache).get(SAMPLE_REQUEST);
        verify(mockRootServiceCall).call(SAMPLE_REQUEST);
        verify(mockCache).put(SAMPLE_REQUEST, SAMPLE_RESPONSE);

        assertThat(response).isEqualTo(SAMPLE_RESPONSE);
    }

    @Test
    public void whenAskingCachedRequestResponseIsReturnedFromCacheWithoutCallingService() {
        when(mockCache.get(SAMPLE_REQUEST))
                .thenReturn(Optional.of(SAMPLE_RESPONSE));

        String response = cachedServiceCall.call(SAMPLE_REQUEST);

        verify(mockCache).get(SAMPLE_REQUEST);
        verify(mockRootServiceCall, never()).call(anyString());

        assertThat(response).isEqualTo(SAMPLE_RESPONSE);
    }


}
