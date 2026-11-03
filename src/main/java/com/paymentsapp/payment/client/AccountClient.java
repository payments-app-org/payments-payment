package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.Payment;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        Map<String, Object> body = new HashMap<>();
        body.put("paymentId", p.getId());
        body.put("userId", p.getUserId());
        body.put("accountId", p.getSourceAccountId());
        body.put("recipientAccountId", p.getRecipientAccountId());
        body.put("operationType", p.getType().name());
        body.put("amount", p.getAmount());
        body.put("commission", p.getCommissionAmount());
        body.put("currency", p.getCurrency());
        rest.post().uri("/api/internal/reservations")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    JsonNode problem = json.readTree(res.getBody());
                    throw new AccountRejectedException(problem.path("code").asString(null),
                            problem.path("detail").asString("Отказ сервиса account"));
                })
                .toBodilessEntity();
    }

    // Наш ли клиент получатель. Пусто — реквизиты не наши, перевод уйдёт в другой банк.
    public Optional<Recipient> findRecipientByPhone(String phone) {
        return findRecipient("phone", phone);
    }

    public Optional<Recipient> findRecipientByCard(String cardNumber) {
        return findRecipient("cardNumber", cardNumber);
    }

    // Имя отправителя, как его увидит получатель
    public String displayName(UUID userId) {
        JsonNode user = rest.get().uri("/api/users/{userId}", userId).retrieve().body(JsonNode.class);
        return user.path("displayName").asString(null);
    }

    private Optional<Recipient> findRecipient(String param, String value) {
        return Optional.ofNullable(rest.get()
                // Значение — через переменную шаблона: так «+» в телефоне кодируется как %2B, а не теряется как пробел
                .uri("/api/internal/recipients?" + param + "={value}", value)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                })
                .body(Recipient.class));
    }

    public record Recipient(UUID userId, String displayName, UUID accountId, String sbpBankId) {
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
