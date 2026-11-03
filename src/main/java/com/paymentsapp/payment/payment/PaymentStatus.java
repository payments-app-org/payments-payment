package com.paymentsapp.payment.payment;

import java.util.EnumSet;
import java.util.Set;

// Шаги саги. Успех: CREATED → RESERVED → FRAUD_APPROVED → SENT_TO_PROCESSING → CONFIRMING → SUCCESS.
// Отказ после резерва: → COMPENSATING (снимаем резерв) → FAILED. Отказ при резерве: CREATED → FAILED.
public enum PaymentStatus {
    CREATED,
    RESERVED,
    FRAUD_APPROVED,
    SENT_TO_PROCESSING,
    CONFIRMING,
    SUCCESS,
    COMPENSATING,
    FAILED;

    public Set<PaymentStatus> next() {
        return switch (this) {
            case CREATED -> EnumSet.of(RESERVED, COMPENSATING, FAILED);
            case RESERVED -> EnumSet.of(FRAUD_APPROVED, COMPENSATING);
            case FRAUD_APPROVED -> EnumSet.of(SENT_TO_PROCESSING, COMPENSATING);
            case SENT_TO_PROCESSING -> EnumSet.of(CONFIRMING, COMPENSATING);
            case CONFIRMING -> EnumSet.of(SUCCESS);
            case COMPENSATING -> EnumSet.of(FAILED);
            case SUCCESS, FAILED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean isFinal() {
        return this == SUCCESS || this == FAILED;
    }
}
