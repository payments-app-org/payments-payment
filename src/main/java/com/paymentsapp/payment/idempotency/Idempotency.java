package com.paymentsapp.payment.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Idempotency {

    private final IdempotencyKeyRepository keys;

    public Idempotency(IdempotencyKeyRepository keys) {
        this.keys = keys;
    }

    // Пусто — ключ захвачен этим запросом; иначе — запись о прошлом запросе с тем же ключом
    @Transactional
    public Optional<IdempotencyKey> acquire(String key, UUID userId, String requestHash) {
        if (keys.tryInsert(key, userId, requestHash) == 1) {
            return Optional.empty();
        }
        return keys.findById(key);
    }

    // Вызывается в транзакции создания платежа
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkPayment(String key, UUID paymentId) {
        keys.findById(key).orElseThrow().linkPayment(paymentId);
    }

    // Временный сбой до создания платежа: освобождаем ключ, чтобы повтор с ним прошёл заново
    @Transactional
    public void release(String key) {
        keys.deleteById(key);
    }

    @Transactional
    public void saveResponse(String key, int status, String body) {
        keys.findById(key).orElseThrow().saveResponse(status, body);
    }
}
