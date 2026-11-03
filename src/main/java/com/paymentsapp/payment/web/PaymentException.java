package com.paymentsapp.payment.web;

import org.springframework.http.HttpStatus;

// Отказ до создания платежа: запрос некорректен, его повтор ничего не изменит
public class PaymentException extends RuntimeException {

    public enum Code {
        INVALID_QUOTE(HttpStatus.BAD_REQUEST),
        QUOTE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT),
        QUOTE_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),
        QUOTE_ALREADY_USED(HttpStatus.CONFLICT),
        INVALID_REQUISITES(HttpStatus.BAD_REQUEST),
        IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT),
        PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT),
        PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND);

        final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }
    }

    private final Code code;

    public PaymentException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
    public HttpStatus getStatus() { return code.status; }
}
