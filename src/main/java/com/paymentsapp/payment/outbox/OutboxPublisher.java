package com.paymentsapp.payment.outbox;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Планировщик: забирает неотправленные события и публикует в Kafka.
// Гарантия «хотя бы один раз»: если упадём после отправки, но до коммита — событие уйдёт повторно.
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH = 100;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-interval}")
    @Transactional
    public void publish() {
        for (OutboxEvent event : repository.lockUnpublished(BATCH)) {
            try {
                kafka.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception e) {
                // Остальные события этой пачки отправим в следующий раз, чтобы не нарушить порядок
                log.warn("Не удалось отправить событие {} в Kafka: {}", event.getId(), e.getMessage());
                return;
            }
        }
    }
}
