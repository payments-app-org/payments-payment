package com.paymentsapp.payment.create;

import java.util.regex.Pattern;

// Форматы реквизитов
final class Formats {

    static final Pattern RU_PHONE = Pattern.compile("\\+7\\d{10}");
    // Идентификатор участника СБП — 12 цифр
    static final Pattern SBP_BANK_ID = Pattern.compile("\\d{12}");
    static final Pattern CARD_NUMBER = Pattern.compile("\\d{16,19}");

    private Formats() {
    }

    static boolean phone(String value) {
        return value != null && RU_PHONE.matcher(value).matches();
    }
}
