package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.idempotency.Idempotency;
import com.paymentsapp.payment.idempotency.IdempotencyKey;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.payment.PaymentSaga;
import com.paymentsapp.payment.payment.PaymentStore;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.payment.quote.Quote;
import com.paymentsapp.payment.quote.QuoteVerifier;
import com.paymentsapp.platform.crypto.Hashing;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.error.Problems;
import com.paymentsapp.platform.text.Rubles;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

// Приём платежа: ключ идемпотентности → котировка → проверка и подготовка своего вида (PaymentPreparer) →
// создание платежа → сага. Повтор с тем же ключом отдаёт прежний результат без второго списания.
@Service
public class PaymentService {

    // Уникальный индекс «одно начисление — один живой платёж»
    static final String BILL_ONCE_CONSTRAINT = "payments_bill_once_uq";

    private final Map<PaymentType, PaymentPreparer> preparers = new EnumMap<>(PaymentType.class);
    private final Idempotency idempotency;
    private final QuoteVerifier quotes;
    private final AccountClient account;
    private final PaymentStore store;
    private final PaymentSaga saga;
    private final RoundUpPolicy roundUp;
    private final PaymentProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    public PaymentService(List<PaymentPreparer> preparers, Idempotency idempotency, QuoteVerifier quotes, AccountClient account,
                          PaymentStore store, PaymentSaga saga, RoundUpPolicy roundUp, PaymentProperties properties,
                          JsonMapper json, Clock clock) {
        preparers.forEach(p -> p.types().forEach(type -> this.preparers.put(type, p)));
        this.idempotency = idempotency;
        this.quotes = quotes;
        this.account = account;
        this.store = store;
        this.saga = saga;
        this.roundUp = roundUp;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public Outcome submit(UUID userId, String key, PaymentRequest req) {
        String requestHash = Hashing.sha256Hex(userId + ":" + json.writeValueAsString(req));
        // Захват ключа до любой другой работы
        Optional<IdempotencyKey> previous = idempotency.acquire(key, userId, requestHash);
        if (previous.isPresent()) {
            return replay(previous.get(), userId, requestHash);
        }
        Created created;
        try {
            created = create(userId, key, req);
        } catch (DomainException e) {
            if (e.getCode().status() == HttpStatus.SERVICE_UNAVAILABLE) {
                // Временный сбой соседа: освобождаем ключ, повтор с ним пройдёт заново
                idempotency.release(key);
            } else {
                idempotency.saveResponse(key, e.getCode().status().value(), json.writeValueAsString(Problems.of(e)));
            }
            throw e;
        }
        return new Outcome.Done(saga.run(created.payment(), created.cardForProcessing()));
    }

    // Котировка, проверка запроса и подготовка своего вида — до внешних шагов саги, чтобы остался след
    private Created create(UUID userId, String key, PaymentRequest req) {
        if (req.amount().compareTo(properties.minAmount()) < 0) {
            throw new DomainException(INVALID_AMOUNT, Rubles.format(properties.minAmount()));
        }
        PaymentPreparer preparer = preparers.get(req.type());
        preparer.validate(req);
        Quote quote = quotes.verify(req.quoteToken(), userId, req.sourceAccountId(), req.type().name(), req.amount(),
                preparer.quoteRecipientKey(req));
        AccountClient.UserInfo user;
        try {
            user = account.user(userId);
        } catch (RestClientException e) {
            throw DomainException.withCause(ACCOUNT_UNAVAILABLE, e);
        }
        Prepared prepared = preparer.prepare(new PreparationContext(userId, req, quote, user));

        Payment payment = new Payment(userId, req.type(), req.sourceAccountId(), prepared.parties(), prepared.recipientPhone(),
                prepared.recipientBankId(), prepared.recipientCardMask(), req.amount(), quote.commission(), quote.tariffId(),
                req.currencyOrDefault(), quote.quoteId(), message(req), OffsetDateTime.now(clock));
        if (prepared.abroad() != null) {
            payment.setAbroad(prepared.abroad(), prepared.payee().detailsJson());
        } else if (prepared.payee() != null) {
            payment.setRequisites(prepared.payee().detailsJson(), prepared.payee().purpose(), prepared.payee().billReference());
        }
        if (prepared.bill() != null) {
            payment.setBill(prepared.bill());
        }
        // Источник — свой счёт в другом банке: процессинг заберёт деньги оттуда по СБП
        payment.setSourceBankId(user.sbpBankId(req.sourceAccountId()));
        payment.setRoundUp(roundUp.of(req.type(), prepared.bill() == null ? null : prepared.bill().providerGroup(),
                req.amount(), user.roundup()));
        try {
            return new Created(store.create(payment, key), prepared.cardForProcessing());
        } catch (DataIntegrityViolationException e) {
            // Уникальный индекс по quote_id: по этой котировке уже платили
            throw new DomainException(violated(e, BILL_ONCE_CONSTRAINT) ? BILL_NOT_PAYABLE : QUOTE_ALREADY_USED);
        }
    }

    // Повтор с тем же ключом: тот же ответ, без повторного списания
    private Outcome replay(IdempotencyKey previous, UUID userId, String requestHash) {
        if (!previous.sameRequest(userId, requestHash)) {
            throw new DomainException(IDEMPOTENCY_KEY_REUSED);
        }
        if (previous.getResponseStatus() != null) {
            return new Outcome.StoredProblem(previous.getResponseStatus(), previous.getResponseBody());
        }
        if (previous.getPaymentId() != null) {
            return new Outcome.Done(store.get(previous.getPaymentId()));
        }
        throw new DomainException(PAYMENT_IN_PROGRESS);
    }

    private static String message(PaymentRequest req) {
        return req.message() == null || req.message().isBlank() ? null : req.message().trim();
    }

    private static boolean violated(DataIntegrityViolationException e, String constraint) {
        return e.getCause() instanceof ConstraintViolationException c && constraint.equals(c.getConstraintName());
    }

    private record Created(Payment payment, String cardForProcessing) {
    }

    // Итог приёма: платёж (завершён или ещё идёт) или сохранённый отказ прошлого запроса с тем же ключом
    public sealed interface Outcome {

        record Done(Payment payment) implements Outcome {
        }

        record StoredProblem(int status, String body) implements Outcome {
        }
    }
}
