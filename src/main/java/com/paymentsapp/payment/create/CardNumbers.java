package com.paymentsapp.payment.create;

// Номер карты: контрольная сумма Луна и маска для хранения (2200 70** **** 3752)
final class CardNumbers {

    private static final int LUHN_BASE = 10;
    private static final int LUHN_MAX_DIGIT = 9;
    private static final int GROUP = 4;
    private static final int BIN_VISIBLE = 6;
    private static final String MASK = "** **** ";

    private CardNumbers() {
    }

    static boolean valid(String number) {
        return number != null && Formats.CARD_NUMBER.matcher(number).matches() && luhn(number);
    }

    static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int n = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                n *= 2;
                if (n > LUHN_MAX_DIGIT) {
                    n -= LUHN_MAX_DIGIT;
                }
            }
            sum += n;
        }
        return sum % LUHN_BASE == 0;
    }

    static String mask(String number) {
        return number.substring(0, GROUP) + " " + number.substring(GROUP, BIN_VISIBLE) + MASK
                + number.substring(number.length() - GROUP);
    }
}
