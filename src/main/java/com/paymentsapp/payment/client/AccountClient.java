package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.Payment;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Резерв, списание и снятие резерва в account. Все вызовы идемпотентны по paymentId — их можно повторять.
// Бизнес-отказ → AccountRejectedException, сбой сети или 5xx → RestClientException.
@Component
public class AccountClient {

    private final RestClient rest;
    private final JsonMapper json;

    public AccountClient(RestClient.Builder builder, JsonMapper json,
                         @Value("${clients.account.url}") String url,
                         @Value("${clients.account.timeout}") Duration timeout) {
        this.rest = RestClients.create(builder, url, timeout);
        this.json = json;
    }

    public void reserve(Payment p) {
        rest.post().uri("/api/internal/reservations")
                .body(Map.of(
                        "paymentId", p.getId(),
                        "userId", p.getUserId(),
                        "accountId", p.getSourceAccountId(),
                        "operationType", p.getType().name(),
                        "amount", p.getAmount(),
                        "commission", p.getCommissionAmount(),
                        "currency", p.getCurrency()))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    JsonNode problem = json.readTree(res.getBody());
                    throw new AccountRejectedException(problem.path("code").asString(null),
                            problem.path("detail").asString("Отказ сервиса account"));
                })
                .toBodilessEntity();
    }

    public void capture(UUID paymentId) {
        rest.post().uri("/api/internal/reservations/{paymentId}/capture", paymentId)
                .retrieve().toBodilessEntity();
    }

    // Резерва нет (резервирование так и не дошло до account) — снимать нечего, это тоже успех
    public void release(UUID paymentId) {
        rest.post().uri("/api/internal/reservations/{paymentId}/release", paymentId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                })
                .toBodilessEntity();
    }
}
