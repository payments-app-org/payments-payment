package com.paymentsapp.payment.client;

import com.paymentsapp.payment.error.PaymentError;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.error.RemoteProblem;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

// biller: способы перевода за рубеж и платежи поставщикам (BILL) — проверка и оплата у поставщика.
// Оплату повторяем только при ошибке соединения; biller сам идемпотентен по paymentId.
@Component
public class BillerClient {

    private final BillerApi api;
    private final ResilientCalls calls;
    private final JsonMapper json;

    BillerClient(BillerApi api, ResilientCalls calls, JsonMapper json) {
        this.api = api;
        this.calls = calls;
        this.json = json;
    }

    public Optional<MethodDetails> method(int id) {
        return calls.call(Backends.BILLER, () -> {
            try {
                return Optional.ofNullable(api.method(id));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    // Проверка платежа поставщику. Отказ biller (4xx) — DomainException с его кодом и текстом.
    public BillCheck checkBill(String providerCode, Map<String, String> fields, BigDecimal amount, UUID chargeId) {
        BillerApi.CheckRequest request = new BillerApi.CheckRequest(providerCode, fields, amount, chargeId);
        return calls.call(Backends.BILLER, () -> {
            try {
                return api.check(request);
            } catch (HttpClientErrorException e) {
                RemoteProblem problem = RemoteProblem.of(e, json, PaymentError.BILLER_REJECTED.template());
                throw DomainException.withMessage(errorFor(problem.code()), problem.detail());
            }
        });
    }

    // Шаг саги: оплата у поставщика. Идемпотентно по paymentId.
    public ProcessingClient.TransferResult pay(UUID paymentId, String providerCode, BigDecimal amount, Map<String, String> fields) {
        BillerApi.PayRequest request = new BillerApi.PayRequest(paymentId, providerCode, amount, fields);
        return calls.call(Backends.BILLER, Backends.BILLER_CONNECT_ONLY, () -> api.pay(request)).toTransferResult();
    }

    // Чем закончилась оплата у поставщика. Пусто — поставщик платёж не получал.
    public Optional<ProcessingClient.TransferResult> findPayment(UUID paymentId) {
        return calls.call(Backends.BILLER, () -> {
            try {
                return Optional.ofNullable(api.payment(paymentId)).map(BillerApi.GatewayResult::toTransferResult);
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    // Код отказа biller → наш код с тем же значением поля code (BILL_NOT_PAYABLE, INVALID_AMOUNT, …)
    private static PaymentError errorFor(String code) {
        return Arrays.stream(PaymentError.values()).filter(e -> e.code().equals(code)).findFirst()
                .orElse(PaymentError.BILLER_REJECTED);
    }

    public record BillCheck(String providerCode, String providerGroup, String recipientName, String purpose,
                            List<BillField> fields, Map<String, String> requisites, String billReference,
                            String recipientPhone) {
    }

    public record BillField(String key, String label, String value, String display) {
    }

    public record MethodDetails(Method method, List<String> currencies, List<Field> fields) {
    }

    public record Method(int id, String countryCode, String name, String kind) {
    }

    public record Field(String key, String label, FieldType type, String pattern) {
    }

    // Тип поля получателя в шаблоне способа перевода за рубеж (biller.template_fields.field_type)
    public enum FieldType {
        TEXT,
        CARD_NUMBER,
        PHONE,
        ACCOUNT,
        IBAN,
        SWIFT,
        IFSC
    }
}
