package com.paymentsapp.payment.web;

import com.paymentsapp.payment.payment.PaymentType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

// SBP_PHONE: recipientPhone + recipientBankId. CARD2CARD: recipientCardNumber.
public record PaymentRequest(
        @NotNull PaymentType type,
        @NotNull UUID sourceAccountId,
        String recipientPhone,
        String recipientBankId,
        String recipientCardNumber,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
        String currency,
        @NotBlank String quoteToken
) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "RUB" : currency;
    }
}
