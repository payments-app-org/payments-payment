package com.paymentsapp.payment.payment;

// Почему платёж не прошёл. Первые коды приходят из account как есть.
public enum FailureCode {
    INSUFFICIENT_FUNDS,
    LIMIT_EXCEEDED,
    ACCOUNT_BLOCKED,
    ACCOUNT_NOT_OWNED,
    ACCOUNT_NOT_FOUND,
    CURRENCY_MISMATCH,
    RECIPIENT_INVALID,
    RECIPIENT_LIMIT_EXCEEDED,
    ACCOUNT_UNAVAILABLE,
    FRAUD_REJECTED,
    FRAUD_UNAVAILABLE,
    PROCESSING_DECLINED,
    PROCESSING_NOT_COMPLETED,
    INTERRUPTED;

    public static FailureCode fromAccount(String code) {
        try {
            return valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ACCOUNT_UNAVAILABLE;
        }
    }
}
