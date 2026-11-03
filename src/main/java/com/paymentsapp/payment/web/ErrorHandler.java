package com.paymentsapp.payment.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(PaymentException.class)
    ProblemDetail paymentRejected(PaymentException e) {
        return PaymentController.problem(e);
    }
}
