package com.dimosr.service;

import com.dimosr.service.core.ServiceCall;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimingOutServiceCall<REQUEST, RESPONSE> implements ServiceCall<REQUEST, RESPONSE> {
    private final ServiceCall<REQUEST, RESPONSE> serviceCall;
    private final long timeout;
    private final TimeUnit accuracy;
    private final ExecutorService executorService;

    /**
     * A ServiceCall that will timeout after the provided time has passed
     * In case of timeout, an UncheckedTimeoutException will be thrown
     *
     * Note: TimeUnit.NANOSECONDS accuracy not supported, due to Java API deficiencies in conversion
     *
     * @param serviceCall, the ServiceCall that will be executed
     * @param timeout, the timeout imposed on the call
     * @param accuracy, the accuracy that will be used for the provided timeout (e.g. milliseconds, microseconds etc.)
     * @param executorService, the executorService that will be used for executing the ServiceCall
     */
    public TimingOutServiceCall(final ServiceCall<REQUEST, RESPONSE> serviceCall,
                                final Duration timeout,
                                final TimeUnit accuracy,
                                final ExecutorService executorService) {
        this.serviceCall = serviceCall;
        this.timeout = getValueRelativeToUnit(timeout, accuracy);
        this.accuracy = accuracy;
        this.executorService = executorService;
    }

    @Override
    public RESPONSE call(REQUEST request) {
        Future<RESPONSE> responseFuture = executorService.submit(() -> serviceCall.call(request));

        try {
            return responseFuture.get(timeout, accuracy);
        } catch (TimeoutException e) {
            String message = String.format("Service call timed out after %d %s", timeout, accuracy);
            throw new UncheckedTimeoutException(message, e);
        } catch(InterruptedException|ExecutionException e) {
            throw new RuntimeException(e);
        }

    }

    private long getValueRelativeToUnit(final Duration duration, final TimeUnit accuracy) {
        switch (accuracy) {
            case NANOSECONDS: return duration.toNanos();
            case MILLISECONDS: return duration.toMillis();
            case SECONDS: return duration.getSeconds();
            case MINUTES: return duration.toMinutes();
            case HOURS: return duration.toHours();
            case DAYS: return duration.toDays();
            default: throw new UnsupportedOperationException("TimeUnit.MICROSECONDS not supported, because conversion to this unit not available from Java");
        }
    }
}
