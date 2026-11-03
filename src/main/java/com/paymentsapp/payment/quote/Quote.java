package com.paymentsapp.payment.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Котировка комиссии, выданная сервисом pricing (тот же формат).
// recipientHash — SHA-256 реквизита, от которого зависел тариф (номер карты получателя); null — тариф от получателя не зависел.
public record Quote(
        UUID quoteId,
        UUID userId,
        UUID sourceAccountId,
        String operationType,
        String destinationType,
        String recipientHash,
        BigDecimal amount,
        BigDecimal commission,
        int tariffId,
        Instant expiresAt,
        // Для переводов за рубеж: валюта и сумма к получению по курсу fxRate (рублей за единицу)
        String receiveCurrency,
        BigDecimal receiveAmount,
        BigDecimal fxRate
) {

    public Quote(UUID quoteId, UUID userId, UUID sourceAccountId, String operationType, String destinationType,
                 String recipientHash, BigDecimal amount, BigDecimal commission, int tariffId, Instant expiresAt) {
        this(quoteId, userId, sourceAccountId, operationType, destinationType, recipientHash, amount, commission,
                tariffId, expiresAt, null, null, null);
    }
}
