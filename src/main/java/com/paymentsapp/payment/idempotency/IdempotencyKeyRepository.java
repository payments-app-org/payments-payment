package com.paymentsapp.payment.idempotency;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    // 1 — ключ наш, 0 — такой запрос уже был
    @Modifying
    @Query(value = """
            insert into idempotency_keys (key, user_id, request_hash, created_at)
            values (:key, :userId, :requestHash, now())
            on conflict (key) do nothing
            """, nativeQuery = true)
    int tryInsert(String key, UUID userId, String requestHash);

    // Одна пачка старых ключей; каждая пачка — своя короткая транзакция
    @Modifying
    @Transactional
    @Query(value = """
            delete from idempotency_keys where key in (
                select key from idempotency_keys where created_at < :before limit :batch)
            """, nativeQuery = true)
    int deleteBatchCreatedBefore(OffsetDateTime before, int batch);
}
