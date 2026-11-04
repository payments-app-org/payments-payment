package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.web.PaymentException.Code.INVALID_REQUISITES;

import java.util.LinkedHashMap;
import java.util.Map;

// Реквизиты российского юрлица: проверяем по правилам ЦБ и ФНС — контрольные числа ИНН, ключ счёта по БИК, формат КПП.
final class RuRequisites {

    // БИК нашего банка — для «Оплаты по счёту юрлицу. Счета нашего банка»
    static final String OUR_BIK = "044525999";

    record Checked(String json, String recipientName, String purpose) {
    }

    private RuRequisites() {
    }

    static Checked check(Map<String, String> fields, boolean ourBank, tools.jackson.databind.json.JsonMapper json) {
        Map<String, String> f = fields == null ? Map.of() : fields;
        String account = digits(f.get("account"));
        String bik = ourBank ? OUR_BIK : digits(f.get("bik"));
        String inn = digits(f.get("inn"));
        String name = trim(f.get("name"));
        String kpp = trim(f.get("kpp")).toUpperCase();
        String purpose = trim(f.get("purpose"));
        String payerName = trim(f.get("payerName"));
        String payerInn = digits(f.get("payerInn"));

        require(bik.matches("04\\d{7}"), "БИК — 9 цифр, начинается с 04");
        require(ourBank || !OUR_BIK.equals(bik), "Это БИК нашего банка — выберите «Счета нашего банка»");
        require(account.matches("\\d{20}") && accountKeyValid(bik, account), "Проверьте номер счёта получателя: не сходится с БИК");
        require(innValid(inn), "Проверьте ИНН получателя");
        require(!name.isEmpty() && name.length() <= 160, "Укажите название компании получателя");
        // КПП есть только у организаций (ИНН из 10 цифр); у ИП его нет
        require(inn.length() == 12 ? kpp.isEmpty() || kpp.matches("0+") : kpp.matches("\\d{4}[\\dA-Z]{2}\\d{3}"),
                "КПП — 9 символов (у ИП не указывается)");
        require(!purpose.isEmpty() && purpose.length() <= 210, "Укажите назначение платежа (до 210 символов)");
        require(!payerName.isEmpty(), "Укажите ФИО отправителя");
        require(payerInn.isEmpty() || innValid(payerInn), "Проверьте ИНН отправителя");

        Map<String, String> stored = new LinkedHashMap<>();
        stored.put("account", account);
        stored.put("bik", bik);
        stored.put("inn", inn);
        stored.put("name", name);
        if (inn.length() == 10) {
            stored.put("kpp", kpp);
        }
        stored.put("payerName", payerName);
        if (!payerInn.isEmpty()) {
            stored.put("payerInn", payerInn);
        }
        return new Checked(json.writeValueAsString(stored), name, purpose);
    }

    // Ключ счёта (ЦБ): последние 3 цифры БИК + 20 цифр счёта, веса 7-1-3 по кругу, сумма должна делиться на 10
    static boolean accountKeyValid(String bik, String account) {
        String s = bik.substring(6) + account;
        int[] w = {7, 1, 3};
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            sum += (s.charAt(i) - '0') * w[i % 3];
        }
        return sum % 10 == 0;
    }

    // Контрольные числа ИНН: 10 цифр — организация, 12 — ИП и физлицо
    static boolean innValid(String inn) {
        if (inn.matches("\\d{10}")) {
            return check(inn, new int[]{2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(9) - '0';
        }
        if (inn.matches("\\d{12}")) {
            return check(inn, new int[]{7, 2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(10) - '0'
                    && check(inn, new int[]{3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8}) == inn.charAt(11) - '0';
        }
        return false;
    }

    private static int check(String inn, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (inn.charAt(i) - '0') * weights[i];
        }
        return sum % 11 % 10;
    }

    private static String digits(String v) {
        return v == null ? "" : v.replaceAll("\\D", "");
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new PaymentException(INVALID_REQUISITES, message);
        }
    }
}
