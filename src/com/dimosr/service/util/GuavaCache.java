package com.dimosr.service.util;

import com.dimosr.service.core.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class GuavaCache<K,V> implements Cache<K,V> {
    private final com.google.common.cache.Cache<K,V> cache;

    public GuavaCache(final int cacheEntries, final int ttlInMilliseconds) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(cacheEntries)
                .expireAfterWrite(ttlInMilliseconds, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public Optional<V> get(K key) {
        V value = cache.getIfPresent(key);
        return Optional.ofNullable(value);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }
}
