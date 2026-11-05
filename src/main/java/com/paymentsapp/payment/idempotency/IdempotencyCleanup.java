package com.paymentsapp.payment.idempotency;

import java.time.Clock;
import java.time.OffsetDateTime;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Ключ идемпотентности нужен, пока клиент может повторить запрос. Без очистки таблица растёт с каждым платежом
// и замедляет вставку. Удаляем пачками: одна большая DELETE держала бы блокировки и раздувала журнал БД.
@Component
public class IdempotencyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanup.class);

    private final IdempotencyKeyRepository keys;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyCleanup(IdempotencyKeyRepository keys, IdempotencyProperties properties, Clock clock) {
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payment.idempotency.cleanup-interval}")
    @SchedulerLock(name = "payment-idempotency-cleanup")
    public void cleanup() {
        OffsetDateTime before = OffsetDateTime.now(clock).minus(properties.retention());
        int total = 0;
        int deleted;
        do {
            deleted = keys.deleteBatchCreatedBefore(before, properties.cleanupBatch());
            total += deleted;
        } while (deleted == properties.cleanupBatch());
        if (total > 0) {
            log.info("Удалено ключей идемпотентности старше {}: {}", properties.retention(), total);
        }
    }
}
