package com.paymentsapp.payment.payment;

import com.paymentsapp.payment.idempotency.Idempotency;
import com.paymentsapp.payment.outbox.Outbox;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Короткие транзакции саги: каждый шаг фиксируется отдельно, между ними — внешние вызовы без открытой транзакции.
// Каждый переход пишется в историю, финальные — ещё и событием в Kafka через outbox.
@Component
public class PaymentStore {

    public static final String TOPIC = "payment.events";

    private final PaymentRepository payments;
    private final StatusChangeRepository history;
    private final Outbox outbox;
    private final Idempotency idempotency;

    public PaymentStore(PaymentRepository payments, StatusChangeRepository history, Outbox outbox,
                        Idempotency idempotency) {
        this.payments = payments;
        this.history = history;
        this.outbox = outbox;
        this.idempotency = idempotency;
    }

    // Платёж и привязка ключа идемпотентности — одной транзакцией
    @Transactional
    public Payment create(Payment payment, String idempotencyKey) {
        payments.saveAndFlush(payment);
        idempotency.linkPayment(idempotencyKey, payment.getId());
        history.save(new StatusChange(payment.getId(), payment.getStatus(), "Платёж создан"));
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow();
    }

    @Transactional
    public Payment move(UUID id, PaymentStatus to, String note) {
        Payment p = get(id);
        p.moveTo(to);
        return saved(p, note);
    }

    @Transactional
    public Payment moveWithProcessingId(UUID id, PaymentStatus to, String processingId) {
        Payment p = get(id);
        p.moveTo(to);
        p.setProcessingId(processingId);
        return saved(p, "Процессинг провёл перевод: " + processingId);
    }

    @Transactional
    public Payment fail(UUID id, PaymentStatus to, FailureCode code, String reason) {
        Payment p = get(id);
        p.moveTo(to);
        p.fail(code, reason);
        return saved(p, code + ": " + reason);
    }

    private Payment saved(Payment p, String note) {
        payments.saveAndFlush(p);
        history.save(new StatusChange(p.getId(), p.getStatus(), note));
        if (p.getStatus().isFinal()) {
            publish(p);
        }
        return p;
    }

    private void publish(Payment p) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", p.getId());
        data.put("userId", p.getUserId());
        data.put("type", p.getType());
        data.put("status", p.getStatus());
        data.put("sourceAccountId", p.getSourceAccountId());
        data.put("senderName", p.getSenderName());
        data.put("recipientUserId", p.getRecipientUserId());
        data.put("recipientAccountId", p.getRecipientAccountId());
        data.put("recipientName", p.getRecipientName());
        data.put("recipientPhone", p.getRecipientPhone());
        data.put("recipientBankId", p.getRecipientBankId());
        data.put("recipientCardMask", p.getRecipientCardMask());
        data.put("message", p.getMessage());
        data.put("sourceExternal", p.isSourceExternal());
        if (p.getType() == PaymentType.ABROAD) {
            data.put("country", p.getCountry());
            data.put("receiveCurrency", p.getReceiveCurrency());
            data.put("receiveAmount", p.getReceiveAmount());
            data.put("fxRate", p.getFxRate());
            data.put("transferMethodName", p.getTransferMethodName());
        }
        data.put("destinationExternal", p.isDestinationExternal());
        data.put("createdAt", p.getCreatedAt());
        data.put("amount", p.getAmount());
        data.put("commission", p.getCommissionAmount());
        data.put("total", p.getTotal());
        data.put("currency", p.getCurrency());
        if (p.getFailureCode() != null) {
            data.put("failureCode", p.getFailureCode());
            data.put("failureReason", p.getFailureReason());
        }
        String eventType = p.getStatus() == PaymentStatus.SUCCESS ? "PaymentSucceeded" : "PaymentFailed";
        outbox.add(TOPIC, p.getId().toString(), eventType, data);
    }
}
