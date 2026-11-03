package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.AccountRejectedException;
import com.paymentsapp.payment.client.FraudClient;
import com.paymentsapp.payment.client.FraudClient.FraudDecision;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// Оркестратор: ведёт платёж по шагам и при отказе откатывает уже сделанное.
// Сбой связи на шаге, где нельзя понять исход, оставляет платёж в промежуточном статусе —
// его дожмёт RecoveryJob (все вызовы идемпотентны по paymentId).
@Component
public class PaymentSaga {

    private static final Logger log = LoggerFactory.getLogger(PaymentSaga.class);

    private final PaymentStore store;
    private final AccountClient account;
    private final FraudClient fraud;
    private final ProcessingClient processing;

    public PaymentSaga(PaymentStore store, AccountClient account, FraudClient fraud, ProcessingClient processing) {
        this.store = store;
        this.account = account;
        this.fraud = fraud;
        this.processing = processing;
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

        // Шаг 5. Процессинг. Статус ставим до вызова: с этого момента деньги могли уйти
        payment = store.move(id, SENT_TO_PROCESSING, "Отправлен в процессинг");
        TransferResult result;
        try {
            result = processing.transfer(payment, recipientCardNumber);
        } catch (RestClientException e) {
            log.warn("payment {}: процессинг не ответил, спрашиваем статус: {}", id, e.getMessage());
            Optional<TransferResult> known = findProcessingResult(id);
            if (known.isEmpty()) {
                // Исход неизвестен — решит RecoveryJob
                return store.get(id);
            }
            result = known.get();
        }
        return afterProcessing(id, result);
    }

    // Шаг 6. Процессинг ответил: проводим списание или откатываем
    Payment afterProcessing(UUID id, TransferResult result) {
        if (!result.completed()) {
            return compensate(id, FailureCode.PROCESSING_DECLINED, result.reason() != null
                    ? result.reason() : "Перевод отклонён банком получателя");
        }
        store.moveWithProcessingId(id, CONFIRMING, result.externalId());
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

    Optional<TransferResult> findProcessingResult(UUID id) {
        try {
            return processing.findResult(id);
        } catch (RestClientException e) {
            log.warn("payment {}: не удалось узнать статус в процессинге: {}", id, e.getMessage());
            return Optional.empty();
        }
    }
}
