package com.paymentsapp.payment.payment;

// Оплата счетов появится позже
public enum PaymentType {
    SBP_PHONE,
    CARD2CARD,
    // Между своими счетами: деньги не покидают банк — без антифрода и процессинга
    OWN_TRANSFER,
    // За рубеж: страна и способ из biller, сумма к получению в валюте по курсу из котировки
    ABROAD
}
