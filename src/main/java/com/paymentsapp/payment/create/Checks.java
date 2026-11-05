package com.paymentsapp.payment.create;

import com.paymentsapp.payment.error.PaymentError;
import com.paymentsapp.platform.error.DomainException;

final class Checks {

    private Checks() {
    }

    static void require(boolean condition, PaymentError error, Object... args) {
        if (!condition) {
            throw new DomainException(error, args);
        }
    }
}
