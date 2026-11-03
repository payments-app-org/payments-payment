package com.paymentsapp.payment.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // SKIP LOCKED — несколько экземпляров сервиса не отправят одно событие дважды
    @Query(value = """
            select * from outbox where published_at is null
            order by created_at limit :batch for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(int batch);
}
