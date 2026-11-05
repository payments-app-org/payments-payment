package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.AccountRejectedException;
import com.paymentsapp.payment.client.BillerClient;
import com.paymentsapp.payment.client.FraudClient;
import com.paymentsapp.payment.client.FraudClient.FraudDecision;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

// Оркестратор: ведёт платёж по шагам и при отказе откатывает уже сделанное.
// Сбой связи на шаге, где нельзя понять исход, оставляет платёж в промежуточном статусе —
// его дожмёт RecoveryJob (все вызовы идемпотентны по paymentId).
@Component
public class PaymentSaga {

    private static final Logger log = LoggerFactory.getLogger(PaymentSaga.class);
    private static final String FIELD_KEY = "key";
    private static final String FIELD_VALUE = "value";

    private final PaymentStore store;
    private final AccountClient account;
    private final FraudClient fraud;
    private final ProcessingClient processing;
    private final BillerClient biller;
    private final SettlementPolicy settlement;
    private final JsonMapper json;

    public PaymentSaga(PaymentStore store, AccountClient account, FraudClient fraud, ProcessingClient processing,
                       BillerClient biller, SettlementPolicy settlement, JsonMapper json) {
        this.settlement = settlement;
        this.store = store;
        this.account = account;
        this.fraud = fraud;
        this.processing = processing;
        this.biller = biller;
        this.json = json;
    }

    public Payment run(Payment payment, String recipientCardNumber) {
        UUID id = payment.getId();

        // Шаг 3. Резерв суммы с комиссией и проверка лимитов
        try {
            account.reserve(payment);
        } catch (AccountRejectedException e) {
            // Резерв не создан — откатывать нечего
            return store.fail(id, FAILED, FailureCode.fromAccount(e.getCode()), e.getMessage());
        } catch (RestClientException e) {
            // Не знаем, создался ли резерв, — снимаем на всякий случай
            log.warn("payment {}: account недоступен при резервировании: {}", id, e.getMessage());
            return compensate(id, FailureCode.ACCOUNT_UNAVAILABLE, "Сервис счетов недоступен, попробуйте позже");
        }
        payment = store.move(id, RESERVED, "Средства зарезервированы: " + payment.getTotal());

        // Между своими счетами деньги не покидают банк: сразу списание и зачисление в account.
        // Исключение — привязанная карта другого банка с любой стороны: деньги двигает процессинг.
        if (payment.staysInBank()) {
            store.move(id, CONFIRMING, "Перевод между своими счетами — без антифрода и процессинга");
            return confirm(id);
        }
        boolean deferred = settlement.isDeferred(payment);

        // Шаг 4. Антифрод
        FraudDecision decision;
        try {
            decision = fraud.check(payment);
        } catch (RestClientException e) {
            log.warn("payment {}: антифрод не ответил: {}", id, e.getMessage());
            return compensate(id, FailureCode.FRAUD_UNAVAILABLE, "Не удалось проверить платёж, попробуйте позже");
        }
        if (!decision.approved()) {
            return compensate(id, FailureCode.FRAUD_REJECTED, decision.reason() != null
                    ? decision.reason() : "Платёж отклонён проверкой безопасности");
        }
        store.move(id, FRAUD_APPROVED, "Антифрод одобрил");

        // Пожертвование: деньги списываем сразу, фонду их одним переводом за день отправит biller
        if (deferred) {
            store.move(id, CONFIRMING, "Пожертвование — фонду перечислим вместе с остальными в конце дня");
            return confirm(id);
        }

        // Шаг 5. Процессинг, а для оплаты поставщику (BILL) — поставщик через biller.
        // Статус ставим до вызова: с этого момента деньги могли уйти
        boolean bill = payment.getType() == PaymentType.BILL;
        payment = store.move(id, SENT_TO_PROCESSING, bill ? "Отправлен поставщику через biller" : "Отправлен в процессинг");
        TransferResult result;
        try {
            result = bill
                    ? biller.pay(id, payment.getBill().providerCode(), payment.getAmount(), billValues(payment))
                    : processing.transfer(payment, recipientCardNumber);
        } catch (RestClientException e) {
            log.warn("payment {}: {} не ответил, спрашиваем статус: {}", id, bill ? "biller" : "процессинг", e.getMessage());
            Optional<TransferResult> known = findProcessingResult(payment);
            if (known.isEmpty()) {
                // Исход неизвестен — решит RecoveryJob
                return store.get(id);
            }
            result = known.get();
        }
        return afterProcessing(payment, result);
    }

    // Шаг 6. Процессинг (или поставщик) ответил: проводим списание или откатываем
    Payment afterProcessing(Payment payment, TransferResult result) {
        UUID id = payment.getId();
        if (!result.completed()) {
            return compensate(id, FailureCode.PROCESSING_DECLINED, result.reason() != null
                    ? result.reason() : "Платёж отклонён получателем");
        }
        String accepted = payment.getType() == PaymentType.BILL ? "Поставщик принял платёж: " : "Процессинг провёл перевод: ";
        store.moveWithProcessingId(id, CONFIRMING, result.externalId(), accepted + result.externalId());
        return confirm(id);
    }

    // Резерв → списание. Если account недоступен, платёж остаётся CONFIRMING и списание повторит RecoveryJob
    Payment confirm(UUID id) {
        try {
            account.capture(id);
        } catch (RestClientException e) {
            log.warn("payment {}: не удалось подтвердить списание, повторим позже: {}", id, e.getMessage());
            return store.get(id);
        }
        return store.move(id, SUCCESS, "Списание подтверждено");
    }

    Payment compensate(UUID id, FailureCode code, String reason) {
        store.fail(id, COMPENSATING, code, reason);
        return release(id);
    }

    // Снятие резерва. Если account недоступен, платёж остаётся COMPENSATING и снятие повторит RecoveryJob
    Payment release(UUID id) {
        try {
            account.release(id);
        } catch (RestClientException e) {
            log.warn("payment {}: не удалось снять резерв, повторим позже: {}", id, e.getMessage());
            return store.get(id);
        }
        return store.move(id, FAILED, "Резерв снят");
    }

    // Значения полей для поставщика: [{key, value, …}] → {key: value}
    private Map<String, String> billValues(Payment p) {
        Map<String, String> values = new LinkedHashMap<>();
        json.readTree(p.getBill().billFields()).forEach(f -> values.put(f.path(FIELD_KEY).asString(), f.path(FIELD_VALUE).asString()));
        return values;
    }

    Optional<TransferResult> findProcessingResult(Payment p) {
        UUID id = p.getId();
        try {
            return p.getType() == PaymentType.BILL ? biller.findPayment(id) : processing.findResult(id);
        } catch (RestClientException e) {
            log.warn("payment {}: не удалось узнать статус в процессинге: {}", id, e.getMessage());
            return Optional.empty();
        }
    }
}
