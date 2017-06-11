package com.dimosr.service.core;

import java.time.Instant;

/**
 * An abstraction used to collect metrics from various components
 *
 * Whether the collector is synchronous or asynchronous depends on each implementation
 * However, note that if the implementation is synchronous this will impose latency
 * increases in the various components that emit metrics
 */
public interface MetricsCollector {
    void putMetric(String namespace, double value, Instant timestamp);
}
