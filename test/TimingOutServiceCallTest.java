import com.dimosr.service.TimingOutServiceCall;
import com.dimosr.service.UncheckedTimeoutException;
import com.dimosr.service.core.ServiceCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TimingOutServiceCallTest {

    @Mock
    private ServiceCall<String, String> underlyingServiceCall;
    @Mock
    private ExecutorService executorService;
    @Mock
    private Future responseFuture;

    private ServiceCall<String, String> timingOutServiceCall;

    private static final TimeUnit ACCURACY = TimeUnit.MILLISECONDS;
    private static final Duration TIMEOUT= Duration.ofMillis(1);

    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";

    @Before
    public void setupServiceCall() {
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, TIMEOUT, ACCURACY, executorService);

        when(executorService.submit(any(Callable.class)))
                .thenReturn(responseFuture);
    }

    @Test
    public void whenUnderlyingServiceRespondsThenResponseIsSuccessfullyReturned() throws InterruptedException, ExecutionException, TimeoutException {
        setupUnderlyingServiceRespondingSuccessfully();

        String response = timingOutServiceCall.call(REQUEST);

        assertThat(response).isEqualTo(RESPONSE);
    }

    @Test(expected = UncheckedTimeoutException.class)
    public void whenUnderlyingCallTimeoutsThenExceptionIsThrown() throws InterruptedException, ExecutionException, TimeoutException {
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(TimeoutException.class);

        String response = timingOutServiceCall.call(REQUEST);
    }

    @Test(expected = RuntimeException.class)
    public void whenUnderlyingCallThrowsExceptionThenRuntimeExceptionIsThrown() throws InterruptedException, ExecutionException, TimeoutException {
        when(responseFuture.get(TIMEOUT.toMillis(), ACCURACY))
                .thenThrow(ExecutionException.class);

        String response = timingOutServiceCall.call(REQUEST);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void timingOutServiceCannotWorkWithMicroseconds() {
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofNanos(10000), TimeUnit.MICROSECONDS, executorService);
    }

    @Test
    public void timingOutServiceCanWorkWithAllOtherTimeUnits() {
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofNanos(10000), TimeUnit.NANOSECONDS, executorService);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofMillis(10000), TimeUnit.MILLISECONDS, executorService);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofSeconds(10000), TimeUnit.SECONDS, executorService);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofMinutes(10000), TimeUnit.MINUTES, executorService);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofHours(10000), TimeUnit.HOURS, executorService);
        timingOutServiceCall = new TimingOutServiceCall<>(underlyingServiceCall, Duration.ofDays(10000), TimeUnit.DAYS, executorService);
    }

    private void setupUnderlyingServiceRespondingSuccessfully() {
        when(underlyingServiceCall.call(REQUEST))
                .thenReturn(RESPONSE);
        when(executorService.submit(any(Callable.class)))
                .thenAnswer(invocation -> {
                    String result = ((Callable<String>) invocation.getArgument(0)).call();
                    return CompletableFuture.completedFuture(result);
                });
    }

}
