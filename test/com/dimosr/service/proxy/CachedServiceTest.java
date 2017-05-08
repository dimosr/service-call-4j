package com.dimosr.service.proxy;

import com.dimosr.service.Cache;
import com.dimosr.service.ServiceCall;
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
public class CachedServiceTest {

    private CachedService<String, String> cachedService;

    @Mock
    private ServiceCall<String, String> mockRootService;
    @Mock
    private Cache<String, String> mockCache;

    private static final String SAMPLE_REQUEST = "request";
    private static final String SAMPLE_RESPONSE = "response";

    @Before
    public void setupCachedService() {
        this.cachedService = new CachedService<>(mockRootService, mockCache);
    }

    @Test
    public void whenAskingUncachedRequestResponseIsReturnedFromServiceAndAlsoCached() {
        when(mockCache.get(SAMPLE_REQUEST))
                .thenReturn(Optional.empty());
        when(mockRootService.call(SAMPLE_REQUEST))
                .thenReturn(SAMPLE_RESPONSE);

        String response = cachedService.call(SAMPLE_REQUEST);

        verify(mockCache).get(SAMPLE_REQUEST);
        verify(mockRootService).call(SAMPLE_REQUEST);
        verify(mockCache).put(SAMPLE_REQUEST, SAMPLE_RESPONSE);

        assertThat(response).isEqualTo(SAMPLE_RESPONSE);
    }

    @Test
    public void whenAskingCachedRequestResponseIsReturnedFromCacheWithoutCallingService() {
        when(mockCache.get(SAMPLE_REQUEST))
                .thenReturn(Optional.of(SAMPLE_RESPONSE));

        String response = cachedService.call(SAMPLE_REQUEST);

        verify(mockCache).get(SAMPLE_REQUEST);
        verify(mockRootService, never()).call(anyString());

        assertThat(response).isEqualTo(SAMPLE_RESPONSE);
    }


}
