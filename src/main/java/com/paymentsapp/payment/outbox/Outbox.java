package com.paymentsapp.payment.outbox;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// Запись события в outbox. Вызывается внутри бизнес-транзакции: событие сохранится только вместе с изменениями.
@Component
public class Outbox {

    private final OutboxRepository repository;
    private final JsonMapper json;

    public Outbox(OutboxRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String topic, String aggregateId, String eventType, Map<String, ?> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("occurredAt", OffsetDateTime.now().toString());
        payload.putAll(data);
        repository.save(new OutboxEvent(topic, aggregateId, eventType, json.writeValueAsString(payload)));
    }
}
