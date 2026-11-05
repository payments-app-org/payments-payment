package com.paymentsapp.payment.payment;

import static com.paymentsapp.payment.payment.PaymentStatus.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Дожимает платежи, застрявшие в промежуточном статусе: недоступный сервис, таймаут, падение посреди саги.
// Пороги больше, чем длится обычный проход саги, чтобы не пересечься с живым запросом.
@Component
public class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);

    private final PaymentRepository payments;
    private final PaymentSaga saga;

    public RecoveryJob(PaymentRepository payments, PaymentSaga saga) {
        this.payments = payments;
        this.saga = saga;
    }

    // При нескольких подах проход делает один (ShedLock), иначе поды дожимали бы одни и те же платежи
    @Scheduled(fixedDelayString = "${recovery.interval}")
    @SchedulerLock(name = "payment-recovery")
    public void recover() {
        // Повторяем идемпотентные шаги account
        each(EnumSet.of(CONFIRMING), Duration.ofSeconds(10), p -> saga.confirm(p.getId()));
        each(EnumSet.of(COMPENSATING), Duration.ofSeconds(10), p -> saga.release(p.getId()));

        // Процессинг (или поставщик через biller) так и не ответил — спрашиваем статус. Не получал — отменяем.
        each(EnumSet.of(SENT_TO_PROCESSING), Duration.ofSeconds(30), p -> saga.findProcessingResult(p)
                .ifPresentOrElse(
                        result -> saga.afterProcessing(p.getId(), result),
                        () -> saga.compensate(p.getId(), FailureCode.PROCESSING_NOT_COMPLETED,
                                "Перевод не был проведён, средства возвращены")));

        // Сервис упал посреди саги до процессинга — деньги никуда не ушли, безопасно отменить
        each(EnumSet.of(CREATED, RESERVED, FRAUD_APPROVED), Duration.ofSeconds(60), p -> saga.compensate(p.getId(),
                FailureCode.INTERRUPTED, "Платёж прерван, средства возвращены"));
    }

    private void each(Set<PaymentStatus> statuses, Duration olderThan, Consumer<Payment> action) {
        for (Payment p : payments.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAt(
                statuses, OffsetDateTime.now().minus(olderThan))) {
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
