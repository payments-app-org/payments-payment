package com.paymentsapp.payment.idempotency;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    // 1 — ключ наш, 0 — такой запрос уже был
    @Modifying
    @Query(value = """
            insert into idempotency_keys (key, user_id, request_hash, created_at)
            values (:key, :userId, :requestHash, now())
            on conflict (key) do nothing
            """, nativeQuery = true)
    int tryInsert(String key, UUID userId, String requestHash);
}
