package com.paymentsapp.payment.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Котировка комиссии, выданная сервисом pricing (тот же формат)
public record Quote(
        UUID quoteId,
        UUID userId,
        UUID sourceAccountId,
        String operationType,
        BigDecimal amount,
        BigDecimal commission,
        int tariffId,
        Instant expiresAt
) {
}
