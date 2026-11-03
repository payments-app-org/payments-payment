package com.paymentsapp.payment.payment;

// Оплата счетов появится позже
public enum PaymentType {
    SBP_PHONE,
    CARD2CARD,
    // Между своими счетами: деньги не покидают банк — без антифрода и процессинга
    OWN_TRANSFER
}
