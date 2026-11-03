package com.paymentsapp.payment.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    private UUID id;

    private String topic;
    private String aggregateId;
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    OutboxEvent(String topic, String aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.topic = topic;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
    }

    void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
}
