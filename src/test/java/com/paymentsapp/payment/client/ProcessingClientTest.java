package com.paymentsapp.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import com.paymentsapp.payment.client.ProcessingClient.TransferStatus;
import com.paymentsapp.platform.resilience.ResilientCalls;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

// Ответ на «чем кончился перевод?» разбирается на три исхода. Ошибка здесь стоила бы денег: принять сбой связи
// за «не получали» — значит вернуть деньги за перевод, который мог пройти.
class ProcessingClientTest {

    private final ProcessingApi api = mock(ProcessingApi.class);
    private final CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
    private final ResilientCalls calls = new ResilientCalls(breakers,
            RetryRegistry.of(RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ofMillis(1)).build()),
            BulkheadRegistry.ofDefaults());
    private final ProcessingClient client = new ProcessingClient(api, calls);
    private final UUID id = UUID.randomUUID();

    @Test
    void foundResult() {
        TransferResult completed = new TransferResult(TransferStatus.COMPLETED, "PRC-1", null);
        when(api.result(id)).thenReturn(completed);

        assertThat(client.findResult(id)).isEqualTo(new ResultLookup.Found(completed));
    }

    @Test
    void notFoundMeansNotReceived() {
        when(api.result(id)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThat(client.findResult(id)).isInstanceOf(ResultLookup.NotReceived.class);
    }

    @Test
    void serverErrorTimeoutAndOpenCircuitMeanUnknown() {
        doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", null, null, null)).when(api).result(id);
        assertThat(client.findResult(id)).isInstanceOf(ResultLookup.Unknown.class);

        doThrow(new ResourceAccessException("Read timed out")).when(api).result(id);
        assertThat(client.findResult(id)).isInstanceOf(ResultLookup.Unknown.class);

        breakers.circuitBreaker(Backends.PROCESSING).transitionToOpenState();
        assertThat(client.findResult(id)).isInstanceOf(ResultLookup.Unknown.class);
    }
}
