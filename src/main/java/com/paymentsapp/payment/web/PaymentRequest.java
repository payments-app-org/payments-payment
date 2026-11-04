package com.paymentsapp.payment.web;

import com.paymentsapp.payment.payment.PaymentType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

// SBP_PHONE: recipientPhone + recipientBankId (необязателен: без него — банк по умолчанию получателя).
// CARD2CARD: recipientCardNumber. OWN_TRANSFER: destinationAccountId — свой счёт зачисления.
// BILL: providerCode + recipientFields — поля формы поставщика из biller; для найденного начисления госуслуг — chargeId.
// ABROAD: country, receiveCurrency, receiveAmount, transferMethodId и recipientFields — поля из шаблона способа в biller.
public record PaymentRequest(
        @NotNull PaymentType type,
        @NotNull UUID sourceAccountId,
        String recipientPhone,
        String recipientBankId,
        String recipientCardNumber,
        UUID destinationAccountId,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
        String currency,
        @NotBlank String quoteToken,
        @Size(max = 40) String message,
        String country,
        String receiveCurrency,
        BigDecimal receiveAmount,
        Integer transferMethodId,
        // Поля получателя: за рубеж — по шаблону способа; юрлицу — account, bik, inn, name, kpp, purpose, payerName, payerInn
        java.util.Map<String, String> recipientFields,
        // Госуслуги: id начисления из biller
        UUID chargeId,
        // Поставщик из biller: оператор связи (yota) или госпоставщик без поиска начислений (rosreestr)
        String providerCode
) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "RUB" : currency;
    }
}
