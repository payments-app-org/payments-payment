package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.web.PaymentException.Code.INVALID_REQUISITES;

import java.util.regex.Pattern;

// Проверка реквизитов получателя
final class Requisites {

    private static final Pattern PHONE = Pattern.compile("\\+7\\d{10}");
    // Идентификатор участника СБП — 12 цифр
    private static final Pattern BANK_ID = Pattern.compile("\\d{12}");
    private static final Pattern CARD = Pattern.compile("\\d{16,19}");

    private Requisites() {
    }

    // Минимальная сумма почти любого платежа
    static final java.math.BigDecimal MIN_AMOUNT = java.math.BigDecimal.TEN;

    static void validate(PaymentRequest req) {
        if (req.amount().compareTo(MIN_AMOUNT) < 0) {
            throw new PaymentException(PaymentException.Code.INVALID_AMOUNT, "Минимальная сумма — 10 ₽");
        }
        switch (req.type()) {
            case SBP_PHONE -> {
                require(req.recipientPhone() != null && PHONE.matcher(req.recipientPhone()).matches(),
                        "Телефон получателя в формате +7XXXXXXXXXX");
                require(req.recipientBankId() == null || BANK_ID.matcher(req.recipientBankId()).matches(),
                        "Некорректный банк получателя");
                require(req.recipientCardNumber() == null, "Для перевода по телефону номер карты не нужен");
            }
            case CARD2CARD -> {
                require(req.recipientCardNumber() != null && CARD.matcher(req.recipientCardNumber()).matches()
                        && luhn(req.recipientCardNumber()), "Некорректный номер карты получателя");
                require(req.recipientPhone() == null && req.recipientBankId() == null,
                        "Для перевода по карте телефон и банк не нужны");
            }
            case ABROAD -> {
                require(req.country() != null && req.receiveCurrency() != null && req.receiveAmount() != null
                        && req.transferMethodId() != null, "Не выбраны страна, валюта или способ перевода");
                require(req.recipientPhone() == null && req.recipientBankId() == null && req.recipientCardNumber() == null
                        && req.destinationAccountId() == null, "Данные получателя передаются в recipientFields");
            }
            case WALLET_TRANSFER -> require(req.recipientPhone() != null && PHONE.matcher(req.recipientPhone()).matches(),
                    "Телефон получателя в формате +7XXXXXXXXXX");
            case REQUISITES, REQUISITES_OUR -> require(req.recipientFields() != null && !req.recipientFields().isEmpty(),
                    "Заполните реквизиты получателя");
            case UIN_PAYMENT -> require(req.chargeId() != null
                    || req.providerCode() != null && req.recipientFields() != null, "Не выбрано начисление");
            case MOBILE_PAYMENT -> {
                require(req.recipientPhone() != null && PHONE.matcher(req.recipientPhone()).matches(),
                        "Телефон в формате +7XXXXXXXXXX");
                require(req.providerCode() != null, "Не определён оператор связи");
            }
            case OWN_TRANSFER -> {
                require(req.destinationAccountId() != null, "Не выбран счёт зачисления");
                require(!req.destinationAccountId().equals(req.sourceAccountId()), "Нельзя перевести на тот же счёт");
                require(req.recipientPhone() == null && req.recipientBankId() == null && req.recipientCardNumber() == null,
                        "Для перевода между своими счетами реквизиты не нужны");
            }
        }
    }

    // 2200700123473752 → 2200 70** **** 3752
    static String maskCard(String number) {
        return number.substring(0, 4) + " " + number.substring(4, 6) + "** **** " + number.substring(number.length() - 4);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new PaymentException(INVALID_REQUISITES, message);
        }
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int n = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                n = n * 2 > 9 ? n * 2 - 9 : n * 2;
            }
            sum += n;
        }
        return sum % 10 == 0;
    }
}
