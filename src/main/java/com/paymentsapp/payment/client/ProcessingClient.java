package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.Payment;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class ProcessingClient {

    private final RestClient rest;

    public ProcessingClient(RestClient.Builder builder,
                            @Value("${clients.processing.url}") String url,
                            @Value("${clients.processing.timeout}") Duration timeout) {
        this.rest = RestClients.create(builder, url, timeout);
    }

    // Номер карты получателя передаём только здесь, в базе payment его нет
    public TransferResult transfer(Payment p, String recipientCardNumber) {
        Map<String, Object> body = new HashMap<>();
        body.put("paymentId", p.getId());
        body.put("type", p.getType().name());
        body.put("amount", p.getAmount());
        body.put("recipientPhone", p.getRecipientPhone());
        body.put("recipientBankId", p.getRecipientBankId());
        body.put("recipientCardNumber", recipientCardNumber);
        return rest.post().uri("/api/processing/transfers").body(body).retrieve().body(TransferResult.class);
    }

    // Узнать, чем закончился перевод, если ответ на transfer не дошёл. Пусто — процессинг перевод не получал.
    public Optional<TransferResult> findResult(UUID paymentId) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/processing/transfers/{paymentId}", paymentId)
                    .retrieve().body(TransferResult.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public record TransferResult(String status, String externalId, String reason) {

        public boolean completed() {
            return "COMPLETED".equals(status);
        }
    }
}
