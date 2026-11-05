package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Реквизиты российского юрлица по правилам ЦБ и ФНС: ключ счёта по БИК, контрольные числа ИНН, формат КПП
@Component
class RuRequisitesValidator {

    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    // БИК: 9 цифр, у банков России начинается с 04
    private static final Pattern BIK = Pattern.compile("04\\d{7}");
    private static final Pattern ACCOUNT = Pattern.compile("\\d{20}");
    private static final Pattern KPP = Pattern.compile("\\d{4}[\\dA-Z]{2}\\d{3}");
    private static final Pattern ZEROS = Pattern.compile("0+");
    private static final Pattern INN_LEGAL = Pattern.compile("\\d{10}");
    private static final Pattern INN_PERSON = Pattern.compile("\\d{12}");
    // Длины полей платёжного поручения
    private static final int NAME_MAX = 160;
    private static final int PURPOSE_MAX = 210;
    // Ключ счёта (ЦБ): последние 3 цифры БИК + 20 цифр счёта, веса 7-1-3 по кругу, сумма делится на 10
    private static final int BIK_TAIL_FROM = 6;
    private static final int[] ACCOUNT_KEY_WEIGHTS = {7, 1, 3};
    private static final int MODULUS_10 = 10;
    private static final int MODULUS_11 = 11;
    // Контрольные числа ИНН (ФНС)
    private static final int[] INN10_WEIGHTS = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_FIRST_WEIGHTS = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] INN12_SECOND_WEIGHTS = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    private final RequisitesProperties properties;
    private final JsonMapper json;

    RuRequisitesValidator(RequisitesProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    record Checked(String json, String recipientName, String purpose) {
    }

    Checked check(Map<String, String> fields, boolean ourBank) {
        Map<String, String> f = fields == null ? Map.of() : fields;
        String account = digits(f.get(Field.ACCOUNT));
        String bik = ourBank ? properties.ourBik() : digits(f.get(Field.BIK));
        String inn = digits(f.get(Field.INN));
        String name = trim(f.get(Field.NAME));
        String kpp = trim(f.get(Field.KPP)).toUpperCase();
        String purpose = trim(f.get(Field.PURPOSE));
        String payerName = trim(f.get(Field.PAYER_NAME));
        String payerInn = digits(f.get(Field.PAYER_INN));
        boolean person = INN_PERSON.matcher(inn).matches();

        require(BIK.matcher(bik).matches(), BIK_FORMAT);
        require(ourBank || !properties.ourBik().equals(bik), OUR_BIK);
        require(ACCOUNT.matcher(account).matches() && accountKeyValid(bik, account), ACCOUNT_KEY);
        require(innValid(inn), RECIPIENT_INN);
        require(!name.isEmpty() && name.length() <= NAME_MAX, RECIPIENT_NAME);
        // КПП есть только у организаций (ИНН из 10 цифр); у ИП его нет
        require(person ? kpp.isEmpty() || ZEROS.matcher(kpp).matches() : KPP.matcher(kpp).matches(), KPP_FORMAT);
        require(!purpose.isEmpty() && purpose.length() <= PURPOSE_MAX, PURPOSE_REQUIRED, PURPOSE_MAX);
        require(!payerName.isEmpty(), PAYER_NAME_REQUIRED);
        require(payerInn.isEmpty() || innValid(payerInn), PAYER_INN);

        Map<String, String> stored = new LinkedHashMap<>();
        stored.put(Field.ACCOUNT, account);
        stored.put(Field.BIK, bik);
        stored.put(Field.INN, inn);
        stored.put(Field.NAME, name);
        if (!person) {
            stored.put(Field.KPP, kpp);
        }
        stored.put(Field.PAYER_NAME, payerName);
        if (!payerInn.isEmpty()) {
            stored.put(Field.PAYER_INN, payerInn);
        }
        return new Checked(json.writeValueAsString(stored), name, purpose);
    }

    static boolean accountKeyValid(String bik, String account) {
        String s = bik.substring(BIK_TAIL_FROM) + account;
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            sum += (s.charAt(i) - '0') * ACCOUNT_KEY_WEIGHTS[i % ACCOUNT_KEY_WEIGHTS.length];
        }
        return sum % MODULUS_10 == 0;
    }

    // 10 цифр — организация, 12 — ИП и физлицо
    static boolean innValid(String inn) {
        if (INN_LEGAL.matcher(inn).matches()) {
            return checkDigit(inn, INN10_WEIGHTS) == digit(inn, INN10_WEIGHTS.length);
        }
        if (INN_PERSON.matcher(inn).matches()) {
            return checkDigit(inn, INN12_FIRST_WEIGHTS) == digit(inn, INN12_FIRST_WEIGHTS.length)
                    && checkDigit(inn, INN12_SECOND_WEIGHTS) == digit(inn, INN12_SECOND_WEIGHTS.length);
        }
        return false;
    }

    private static int checkDigit(String inn, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += digit(inn, i) * weights[i];
        }
        return sum % MODULUS_11 % MODULUS_10;
    }

    private static int digit(String s, int index) {
        return s.charAt(index) - '0';
    }

    private static String digits(String v) {
        return v == null ? "" : NON_DIGITS.matcher(v).replaceAll("");
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    // Ключи полей реквизитов в запросе и в сохранённом JSON
    static final class Field {
        static final String ACCOUNT = "account";
        static final String BIK = "bik";
        static final String INN = "inn";
        static final String NAME = "name";
        static final String KPP = "kpp";
        static final String PURPOSE = "purpose";
        static final String PAYER_NAME = "payerName";
        static final String PAYER_INN = "payerInn";

        private Field() {
        }
    }
}
