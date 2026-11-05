package com.paymentsapp.payment.incoming;

import static com.paymentsapp.payment.error.PaymentError.ACCOUNT_UNAVAILABLE;
import static com.paymentsapp.payment.error.PaymentError.INCOMING_RECIPIENT_NOT_FOUND;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.payment.PaymentRepository;
import com.paymentsapp.payment.payment.PaymentSaga;
import com.paymentsapp.payment.payment.PaymentStore;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

// Входящий перевод по СБП: в другом банке отправили деньги на телефон нашего клиента, СБП (через процессинг) просит
// зачислить. Идемпотентно по номеру операции СБП: повтор вернёт тот же итог и не зачислит второй раз.
// Получатель — по телефону; счёт зачисления — карта для входящих СБП из профиля, иначе «Мой кошелёк».
@Service
public class IncomingSbpService {

    private static final String RUB = "RUB";

    private final PaymentRepository payments;
    private final PaymentStore store;
    private final PaymentSaga saga;
    private final AccountClient account;
    private final Clock clock;

    public IncomingSbpService(PaymentRepository payments, PaymentStore store, PaymentSaga saga, AccountClient account,
                              Clock clock) {
        this.payments = payments;
        this.store = store;
        this.saga = saga;
        this.account = account;
        this.clock = clock;
    }

    public Payment receive(Incoming in) {
        Optional<Payment> known = existing(in.operationId());
        if (known.isPresent()) {
            return resumed(known.get());
        }
        AccountClient.Recipient recipient;
        try {
            recipient = account.findRecipientByPhone(in.recipientPhone())
                    .orElseThrow(() -> new DomainException(INCOMING_RECIPIENT_NOT_FOUND));
        } catch (RestClientException e) {
            throw DomainException.withCause(ACCOUNT_UNAVAILABLE, e);
        }
        UUID target = recipient.accountId() != null ? recipient.accountId() : recipient.walletAccountId();
        if (target == null) {
            throw new DomainException(INCOMING_RECIPIENT_NOT_FOUND);
        }
        Payment payment = Payment.incoming(in.operationId(), recipient.userId(), target,
                recipient.displayName(), in.recipientPhone(), in.senderName(), in.senderBankId(), in.amount(), RUB,
                in.message(), OffsetDateTime.now(clock));
        try {
            payment = store.createIncoming(payment);
        } catch (DataIntegrityViolationException e) {
            // Тот же запрос СБП пришёл параллельно и успел раньше
            return existing(in.operationId()).orElseThrow(() -> e);
        }
        return saga.creditIncoming(payment);
    }

    // Повтор от СБП: платёж уже есть. Если зачисление тогда не прошло из-за сбоя связи — пробуем ещё раз.
    private Payment resumed(Payment p) {
        return p.getStatus().isFinal() ? p : saga.creditIncoming(p);
    }

    private Optional<Payment> existing(String operationId) {
        return payments.findByTypeAndProcessingId(PaymentType.INCOMING_SBP, operationId);
    }

    public record Incoming(String operationId, String senderName, String senderBankId, String recipientPhone,
                           BigDecimal amount, String message) {
    }
}
