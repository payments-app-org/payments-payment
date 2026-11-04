package com.paymentsapp.payment.web;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.PaymentType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

// «Округление на пользу»: на какие платежи действует и на сколько округлять.
// Не трогаем переводы людям, пополнения, погашение кредитов, транспорт, электронные деньги, зарубежную связь
// и сами пожертвования — только оплаты услуг и свободные реквизиты.
final class RoundUp {

    private static final Set<String> BILL_GROUPS = Set.of("MOBILE", "GAMES", "UTILITIES", "GOV", "INTERNET", "EDUCATION",
            "SHOPS", "TRAVEL", "INSURANCE", "SECURITY");

    private RoundUp() {
    }

    static BigDecimal of(PaymentType type, String providerGroup, BigDecimal amount, AccountClient.Roundup settings) {
        if (settings == null) {
            return BigDecimal.ZERO;
        }
        boolean eligible = type == PaymentType.REQUISITES || type == PaymentType.REQUISITES_OUR
                || type == PaymentType.BILL && BILL_GROUPS.contains(providerGroup);
        if (!eligible) {
            return BigDecimal.ZERO;
        }
        BigDecimal step = BigDecimal.valueOf(settings.step());
        BigDecimal rounded = amount.divide(step, 0, RoundingMode.CEILING).multiply(step);
        return rounded.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
