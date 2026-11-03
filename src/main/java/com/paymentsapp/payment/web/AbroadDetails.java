package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.web.PaymentException.Code.*;

import com.paymentsapp.payment.client.BillerClient.Field;
import com.paymentsapp.payment.client.BillerClient.MethodDetails;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.quote.Quote;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

// Проверка перевода за рубеж по справочнику biller: способ из этой страны, валюта доступна,
// котировка на ту же сумму к получению, поля получателя заполнены по шаблону способа.
final class AbroadDetails {

    // Что сохранить в платеже, номер карты для процессинга (в базу не попадает) и как подписать получателя
    record Checked(Payment.Abroad abroad, String cardNumber, String recipientName) {
    }

    private AbroadDetails() {
    }

    static Checked check(PaymentRequest req, Quote quote, MethodDetails details, JsonMapper json) {
        if (!details.method().countryCode().equalsIgnoreCase(req.country())) {
            throw new PaymentException(INVALID_REQUISITES, "Способ перевода недоступен для этой страны");
        }
        if (!details.currencies().contains(req.receiveCurrency())) {
            throw new PaymentException(INVALID_REQUISITES, "В эту страну нельзя перевести в валюте " + req.receiveCurrency());
        }
        if (!req.receiveCurrency().equals(quote.receiveCurrency()) || quote.receiveAmount() == null
                || quote.receiveAmount().compareTo(req.receiveAmount()) != 0) {
            throw new PaymentException(QUOTE_MISMATCH, "Курс пересчитан на другую сумму, обновите расчёт");
        }

        Map<String, String> given = req.recipientFields() == null ? Map.of() : req.recipientFields();
        Map<String, String> stored = new LinkedHashMap<>();
        String cardNumber = null;
        for (Field f : details.fields()) {
            String value = normalize(f, given.get(f.key()));
            if (value == null || value.isEmpty()) {
                throw new PaymentException(INVALID_REQUISITES, "Заполните поле «" + f.label() + "»");
            }
            if (!Pattern.matches(f.pattern(), value) || "CARD_NUMBER".equals(f.type()) && !luhn(value)) {
                throw new PaymentException(INVALID_REQUISITES, "Проверьте поле «" + f.label() + "»");
            }
            if ("CARD_NUMBER".equals(f.type())) {
                cardNumber = value;
                stored.put(f.key(), Requisites.maskCard(value));
            } else {
                stored.put(f.key(), value);
            }
        }
        String first = stored.get("first_name"), last = stored.get("last_name");
        String name = first != null && last != null ? first + " " + last : details.method().name();
        return new Checked(new Payment.Abroad(req.country().toUpperCase(), req.receiveCurrency(), quote.receiveAmount(),
                quote.fxRate(), details.method().id(), details.method().name(), json.writeValueAsString(stored)),
                cardNumber, name);
    }

    // Номера, IBAN и телефоны — без пробелов; IBAN, SWIFT, IFSC и имена латиницей — заглавными
    private static String normalize(Field f, String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return switch (f.type()) {
            case "CARD_NUMBER", "PHONE", "ACCOUNT" -> v.replaceAll("\\s", "").toUpperCase();
            case "IBAN", "SWIFT", "IFSC" -> v.replaceAll("\\s", "").toUpperCase();
            default -> f.key().endsWith("_name") ? v.toUpperCase() : v;
        };
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
