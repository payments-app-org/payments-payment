package com.paymentsapp.payment.payment;

import com.paymentsapp.payment.idempotency.Idempotency;
import com.paymentsapp.platform.outbox.Outbox;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Короткие транзакции саги: каждый шаг фиксируется отдельно, между ними — внешние вызовы без открытой транзакции.
// Каждый переход пишется в историю, финальные — ещё и событием в Kafka через outbox.
@Component
public class PaymentStore {

    private static final TypeReference<Map<String, Object>> AS_MAP = new TypeReference<>() { };
    private static final String CREATED_NOTE = "Платёж создан";

    private final PaymentRepository payments;
    private final StatusChangeRepository history;
    private final Outbox outbox;
    private final Idempotency idempotency;
    private final SettlementPolicy settlement;
    private final JsonMapper json;
    private final Clock clock;
    private final String topic;

    public PaymentStore(PaymentRepository payments, StatusChangeRepository history, Outbox outbox, Idempotency idempotency,
                        SettlementPolicy settlement, JsonMapper json, Clock clock,
                        @Value("${messaging.payment-events.topic}") String topic) {
        this.payments = payments;
        this.history = history;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.settlement = settlement;
        this.json = json;
        this.clock = clock;
        this.topic = topic;
    }

    // Платёж и привязка ключа идемпотентности — одной транзакцией
    @Transactional
    public Payment create(Payment payment, String idempotencyKey) {
        payments.saveAndFlush(payment);
        idempotency.linkPayment(idempotencyKey, payment.getId());
        history.save(new StatusChange(payment.getId(), payment.getStatus(), CREATED_NOTE, now()));
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow();
    }

    @Transactional
    public Payment move(UUID id, PaymentStatus to, String note) {
        Payment p = get(id);
        p.moveTo(to, now());
        return saved(p, note);
    }

    @Transactional
    public Payment moveWithProcessingId(UUID id, PaymentStatus to, String processingId, String note) {
        Payment p = get(id);
        p.moveTo(to, now());
        p.setProcessingId(processingId);
        return saved(p, note);
    }

    @Transactional
    public Payment fail(UUID id, PaymentStatus to, FailureCode code, String reason) {
        Payment p = get(id);
        p.moveTo(to, now());
        p.fail(code, reason);
        return saved(p, code + ": " + reason);
    }

    private Payment saved(Payment p, String note) {
        payments.saveAndFlush(p);
        history.save(new StatusChange(p.getId(), p.getStatus(), note, now()));
        if (p.getStatus().isFinal()) {
            PaymentEvent event = PaymentEvent.of(p, settlement.isDeferred(p));
            outbox.add(topic, p.getId().toString(), event.eventType(), json.convertValue(event, AS_MAP));
        }
        return p;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
