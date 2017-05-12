package com.dimosr.service.core;

import java.util.Optional;

/**
 * An abstraction of a cache
 *
 * Whether the cache is a local or a distributed one
 * depends on each implementation
 */
public interface Cache<K,V> {
    Optional<V> get(K key);
    void put(K key, V value);
}
