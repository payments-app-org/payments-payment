package com.paymentsapp.payment.client;

import com.paymentsapp.payment.web.PaymentException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Сервис biller: способы перевода за рубеж и платежи поставщикам (BILL) — проверка и оплата у поставщика
@Component
public class BillerClient {

    private final RestClient rest;
    private final JsonMapper json;

    public BillerClient(RestClient.Builder builder,
                        @Value("${clients.biller.url}") String url,
                        @Value("${clients.biller.timeout}") Duration timeout,
                        JsonMapper json) {
        this.rest = RestClients.create(builder, url, timeout);
        this.json = json;
    }

    public Optional<MethodDetails> method(int id) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/biller/methods/{id}", id).retrieve().body(MethodDetails.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // Проверка платежа поставщику. Отказ biller (400/422) — PaymentException с его кодом и текстом.
    public BillCheck checkBill(String providerCode, Map<String, String> fields, BigDecimal amount, UUID chargeId) {
        try {
            return rest.post().uri("/api/biller/bills/check")
                    .body(new CheckRequest(providerCode, fields, amount, chargeId))
                    .retrieve().body(BillCheck.class);
        } catch (HttpClientErrorException e) {
            JsonNode problem = json.readTree(e.getResponseBodyAsString());
            PaymentException.Code code;
            try {
                code = PaymentException.Code.valueOf(problem.path("code").asString("INVALID_REQUISITES"));
            } catch (IllegalArgumentException unknown) {
                code = PaymentException.Code.INVALID_REQUISITES;
            }
            throw new PaymentException(code, problem.path("detail").asString("Проверьте данные платежа"));
        }
    }

    // Шаг саги: оплата у поставщика. Идемпотентно по paymentId.
    public ProcessingClient.TransferResult pay(UUID paymentId, String providerCode, BigDecimal amount, Map<String, String> fields) {
        GatewayResult r = rest.post().uri("/api/biller/payments")
                .body(Map.of("paymentId", paymentId, "providerCode", providerCode, "amount", amount, "fields", fields))
                .retrieve().body(GatewayResult.class);
        return r.toTransferResult();
    }

    // Чем закончилась оплата у поставщика. Пусто — поставщик платёж не получал.
    public Optional<ProcessingClient.TransferResult> findPayment(UUID paymentId) {
        try {
            return Optional.ofNullable(rest.get().uri("/api/biller/payments/{id}", paymentId).retrieve()
                    .body(GatewayResult.class)).map(GatewayResult::toTransferResult);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    record CheckRequest(String providerCode, Map<String, String> fields, BigDecimal amount, UUID chargeId) {
    }

    public record BillCheck(String providerCode, String providerGroup, String recipientName, String purpose,
                            List<BillField> fields, Map<String, String> requisites, String billReference,
                            String recipientPhone) {
    }

    public record BillField(String key, String label, String value, String display) {
    }

    record GatewayResult(UUID paymentId, String status, String externalId, String voucherCode, String message) {

        ProcessingClient.TransferResult toTransferResult() {
            return new ProcessingClient.TransferResult("ACCEPTED".equals(status) ? "COMPLETED" : "DECLINED", externalId,
                    "ACCEPTED".equals(status) ? null : message);
        }
    }

    public record MethodDetails(Method method, List<String> currencies, List<Field> fields) {
    }

    public record Method(int id, String countryCode, String name, String kind) {
    }

    public record Field(String key, String label, String type, String pattern) {
    }
}
