package com.paymentsapp.payment.client;

// account отказал по бизнес-причине (4xx с кодом): недостаточно средств, лимит и т. п.
public class AccountRejectedException extends RuntimeException {

    private final String code;

    public AccountRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
