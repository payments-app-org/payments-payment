package com.paymentsapp.payment.payment;

import java.util.EnumSet;
import java.util.Set;

// Шаги саги. Успех: CREATED → RESERVED → FRAUD_APPROVED → SENT_TO_PROCESSING → CONFIRMING → SUCCESS.
// Долгий перевод: … → SENT_TO_PROCESSING → ACCEPTED (процессинг принял, итог придёт позже) → CONFIRMING → SUCCESS.
// Отказ после резерва: → COMPENSATING (снимаем резерв) → FAILED. Отказ при резерве: CREATED → FAILED.
// Между своими счетами и входящий СБП — одна операция в account: CREATED → SUCCESS / FAILED.
// Пожертвование: … → FRAUD_APPROVED → CONFIRMING → SUCCESS.
public enum PaymentStatus {
    CREATED,
    RESERVED,
    FRAUD_APPROVED,
    SENT_TO_PROCESSING,
    ACCEPTED,
    CONFIRMING,
    SUCCESS,
    COMPENSATING,
    FAILED;

    public Set<PaymentStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(RESERVED, SUCCESS, COMPENSATING, FAILED);
            case RESERVED -> EnumSet.of(FRAUD_APPROVED, CONFIRMING, COMPENSATING);
            // Пожертвование: после антифрода сразу списание, фонду переведёт biller в конце дня
            case FRAUD_APPROVED -> EnumSet.of(SENT_TO_PROCESSING, CONFIRMING, COMPENSATING);
            case SENT_TO_PROCESSING -> EnumSet.of(ACCEPTED, CONFIRMING, COMPENSATING);
            case ACCEPTED -> EnumSet.of(CONFIRMING, COMPENSATING);
            case CONFIRMING -> EnumSet.of(SUCCESS);
            case COMPENSATING -> EnumSet.of(FAILED);
            case SUCCESS, FAILED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED;
    }

    // Ждём итог от процессинга или поставщика: только он может решить судьбу денег
    public boolean awaitsProcessing() {
        return this == SENT_TO_PROCESSING || this == ACCEPTED;
    }
}
