package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Дожимает платежи, застрявшие в промежуточном статусе: недоступный сервис, тайм-аут, падение посреди саги.
// Пороги и размер пачки — payment.recovery.* в application.yml.
@Component
public class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);

    private final PaymentRepository payments;
    private final PaymentSaga saga;
    private final RecoveryProperties properties;
    private final Clock clock;

    public RecoveryJob(PaymentRepository payments, PaymentSaga saga, RecoveryProperties properties, Clock clock) {
        this.payments = payments;
        this.saga = saga;
        this.properties = properties;
        this.clock = clock;
    }

    // При нескольких подах проход делает один (ShedLock), иначе поды дожимали бы одни и те же платежи
    @Scheduled(fixedDelayString = "${payment.recovery.interval}")
    @SchedulerLock(name = "payment-recovery")
    public void recover() {
        // Повторяем идемпотентные шаги account
        each(EnumSet.of(CONFIRMING), properties.confirmAfter(), p -> saga.confirm(p.getId()));
        each(EnumSet.of(COMPENSATING), properties.releaseAfter(), p -> saga.release(p.getId()));

        // Процессинг (или поставщик через biller) так и не ответил — спрашиваем статус. Возвращаем деньги, только
        // если он точно не получал перевод; если узнать не удалось (сосед лежит) — ждём, деньги могли уйти.
        each(EnumSet.of(SENT_TO_PROCESSING), properties.processingAfter(), p -> saga.afterLookup(p, saga.lookup(p)));
        // Долгий перевод: итог процессинг пришлёт сам, а здесь — подстраховка, если уведомление потерялось
        each(EnumSet.of(ACCEPTED), properties.acceptedCheckAfter(), p -> saga.afterLookup(p, saga.lookup(p)));

        // Сервис упал посреди саги до процессинга — деньги никуда не ушли. Внутри банка и входящий СБП —
        // повторяем идемпотентную операцию в account (повтор вернёт уже проведённую); остальное безопасно отменить.
        each(EnumSet.of(CREATED, RESERVED, FRAUD_APPROVED), properties.interruptedAfter(), this::resume);
    }

    private void resume(Payment p) {
        if (p.getStatus() == CREATED && p.staysInBank()) {
            saga.transferInBank(p);
        } else if (p.getStatus() == CREATED && p.getType() == PaymentType.INCOMING_SBP) {
            saga.creditIncoming(p);
        } else {
            saga.compensate(p.getId(), FailureCode.INTERRUPTED, "Платёж прерван, средства возвращены");
        }
    }

    private void each(Set<PaymentStatus> statuses, Duration olderThan, Consumer<Payment> action) {
        for (Payment p : payments.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAt(
                statuses, OffsetDateTime.now(clock).minus(olderThan), Limit.of(properties.batchSize()))) {
            try {
                log.info("payment {}: восстанавливаем из статуса {}", p.getId(), p.getStatus());
                action.accept(p);
            } catch (RuntimeException e) {
                // Например, платёж параллельно сдвинули (оптимистичная блокировка) — разберёмся в следующий проход
                log.warn("payment {}: восстановление не удалось: {}", p.getId(), e.getMessage());
            }
        }
    }
}
