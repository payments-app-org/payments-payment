package com.paymentsapp.payment.client;

// Тип счёта в account
public enum AccountKind {
    WALLET,
    CARD,
    EXTERNAL_CARD,
    EXTERNAL_SBP;

    // Деньги на нём двигает процессинг (карта или счёт другого банка)
    public boolean isExternal() {
        return this == EXTERNAL_CARD || this == EXTERNAL_SBP;
    }
}
