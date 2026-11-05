package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.AccountRejectedException;
import com.paymentsapp.payment.client.BillerClient;
import com.paymentsapp.payment.client.FraudClient;
import com.paymentsapp.payment.client.FraudClient.FraudDecision;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import com.paymentsapp.payment.client.ResultLookup;
import java.util.LinkedHashMap;
import java.util.Map;
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

        // Деньги не покидают банк: проверки, списание и зачисление — одна транзакция в account,
        // без промежуточного резерва, антифрода и процессинга
        if (payment.staysInBank()) {
            return transferInBank(payment);
        }

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
            // Сразу после тайм-аута «не получали» ещё ничего не значит — запрос мог быть в пути. Решает RecoveryJob
            // через processing-after; здесь используем только известный итог.
            return lookup(payment) instanceof ResultLookup.Found found ? afterProcessing(payment, found.result())
                    : store.get(id);
        }
        return afterProcessing(payment, result);
    }

    // Шаг 6. Процессинг (или поставщик) ответил: проводим списание, откатываем или ждём итог
    public Payment afterProcessing(Payment payment, TransferResult result) {
        UUID id = payment.getId();
        if (result.inProgress()) {
            // Долгий перевод: деньги в резерве, итог процессинг пришлёт сам (а RecoveryJob спросит на всякий случай)
            return payment.getStatus() == ACCEPTED ? payment
                    : store.moveWithProcessingId(id, ACCEPTED, result.externalId(), acceptedNote(payment));
        }
        if (!result.completed()) {
            return compensate(id, FailureCode.PROCESSING_DECLINED, result.reason() != null
                    ? result.reason() : "Платёж отклонён получателем");
        }
        String accepted = payment.getType() == PaymentType.BILL ? "Поставщик принял платёж: " : "Процессинг провёл перевод: ";
        store.moveWithProcessingId(id, CONFIRMING, result.externalId(), accepted + result.externalId());
        return confirm(id);
    }

    // Итог от процессинга (уведомление). Повтор или опоздавшее уведомление — платёж уже не ждёт, ничего не делаем.
    public Payment onProcessingResult(UUID id, TransferResult result) {
        Payment payment = store.get(id);
        if (!payment.getStatus().awaitsProcessing()) {
            log.info("payment {}: итог процессинга {} пришёл в статусе {} — уже учтён", id, result.status(), payment.getStatus());
            return payment;
        }
        return afterProcessing(payment, result);
    }

    // Чем закончился перевод, если ответа не было или перевод долгий
    public ResultLookup lookup(Payment p) {
        return p.getType() == PaymentType.BILL ? biller.findPayment(p.getId()) : processing.findResult(p.getId());
    }

    // Итог запроса статуса. «Не получали» — снимаем резерв; «не удалось узнать» — ждём: деньги могли уйти.
    // Платёж в ACCEPTED процессинг уже принял — «не получали» тут противоречие, деньги не трогаем.
    public Payment afterLookup(Payment payment, ResultLookup lookup) {
        UUID id = payment.getId();
        return switch (lookup) {
            case ResultLookup.Found found -> afterProcessing(payment, found.result());
            case ResultLookup.NotReceived ignored when payment.getStatus() == SENT_TO_PROCESSING ->
                    compensate(id, FailureCode.PROCESSING_NOT_COMPLETED, "Перевод не был проведён, средства возвращены");
            case ResultLookup.NotReceived ignored -> {
                log.error("payment {}: процессинг принял перевод, а теперь его не знает — нужен ручной разбор", id);
                yield store.touch(id);
            }
            case ResultLookup.Unknown unknown -> {
                log.warn("payment {}: итог перевода пока неизвестен ({}), спросим позже", id, unknown.reason());
                yield store.touch(id);
            }
        };
    }

    // Резерв → списание. Если account недоступен, платёж остаётся CONFIRMING и списание повторит RecoveryJob
    public Payment confirm(UUID id) {
        try {
            account.capture(id);
        } catch (RestClientException e) {
            log.warn("payment {}: не удалось подтвердить списание, повторим позже: {}", id, e.getMessage());
            return store.get(id);
        }
        return store.move(id, SUCCESS, "Списание подтверждено");
    }

    // Перевод внутри банка одной операцией. Вызов идемпотентен по paymentId: при сбое связи платёж остаётся CREATED,
    // и RecoveryJob повторит его — account либо проведёт перевод, либо вернёт уже проведённый.
    public Payment transferInBank(Payment payment) {
        UUID id = payment.getId();
        try {
            account.transfer(payment);
        } catch (AccountRejectedException e) {
            return store.fail(id, FAILED, FailureCode.fromAccount(e.getCode()), e.getMessage());
        } catch (RestClientException e) {
            log.warn("payment {}: account не ответил на перевод внутри банка, повторим позже: {}", id, e.getMessage());
            return store.get(id);
        }
        return store.move(id, SUCCESS, "Списание и зачисление проведены одной операцией в account");
    }

    // Входящий СБП: зачисление на счёт получателя. Сбой связи — повторит RecoveryJob (и сама СБП повторит запрос).
    public Payment creditIncoming(Payment payment) {
        UUID id = payment.getId();
        try {
            account.credit(payment);
        } catch (AccountRejectedException e) {
            return store.fail(id, FAILED, FailureCode.fromAccount(e.getCode()), e.getMessage());
        } catch (RestClientException e) {
            log.warn("payment {}: account не ответил на зачисление, повторим позже: {}", id, e.getMessage());
            return store.get(id);
        }
        return store.move(id, SUCCESS, "Зачислено на счёт получателя");
    }

    public Payment compensate(UUID id, FailureCode code, String reason) {
        store.fail(id, COMPENSATING, code, reason);
        return release(id);
    }

    // Снятие резерва. Если account недоступен, платёж остаётся COMPENSATING и снятие повторит RecoveryJob
    public Payment release(UUID id) {
        try {
            account.release(id);
        } catch (RestClientException e) {
            log.warn("payment {}: не удалось снять резерв, повторим позже: {}", id, e.getMessage());
            return store.get(id);
        }
        return store.move(id, FAILED, "Резерв снят");
    }

    private static String acceptedNote(Payment p) {
        return p.getType() == PaymentType.BILL ? "Поставщик принял платёж, проведёт позже"
                : "Процессинг принял перевод, итог придёт позже";
    }

    // Значения полей для поставщика: [{key, value, …}] → {key: value}
    private Map<String, String> billValues(Payment p) {
        Map<String, String> values = new LinkedHashMap<>();
        json.readTree(p.getBill().billFields()).forEach(f -> values.put(f.path(FIELD_KEY).asString(), f.path(FIELD_VALUE).asString()));
        return values;
    }
}
