package com.dimosr.service.util;

import com.dimosr.service.core.Cache;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class GuavaCacheTest {

    private Cache<String, String> cache;

    private static final String KEY = "key";
    private static final String VALUE = "value";

    @Before
    public void setup() {
        cache = new GuavaCache<>(10, 100);
    }

    @Test
    public void testGetReturnsEmptyOptionalWhenNoValueInCache() {
        Optional<String> value = cache.get(KEY);

        assertThat(value.isPresent()).isFalse();
    }

    @Test
    public void testPutInsertsTheValueThatIsReturnedInSubsequentGet() {
        cache.put(KEY, VALUE);
        Optional<String> value = cache.get(KEY);

        assertThat(value.isPresent()).isTrue();
        assertThat(value.get()).isEqualTo(VALUE);
    }

}
