package com.paymentsapp.payment.create;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.payment.payment.RoundUp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

// «Округление на пользу»: на какие платежи действует (payment.roundup.*) и на сколько округлять.
// Покупка 85 ₽ при шаге 10 → спишем 90 ₽, 5 ₽ уйдут в фонд.
@ConfigurationProperties("payment.roundup")
public record RoundUpPolicy(Set<PaymentType> types, Set<String> billGroups) {

    private static final int KOPECKS = 2;

    public RoundUp of(PaymentType type, String providerGroup, BigDecimal amount, AccountClient.Roundup settings) {
        boolean eligible = types.contains(type) || type == PaymentType.BILL && billGroups.contains(providerGroup);
        if (settings == null || !eligible) {
            return RoundUp.NONE;
        }
        BigDecimal step = BigDecimal.valueOf(settings.step());
        BigDecimal difference = amount.divide(step, 0, RoundingMode.CEILING).multiply(step).subtract(amount)
                .setScale(KOPECKS, RoundingMode.HALF_UP);
        return difference.signum() > 0 ? new RoundUp(difference, settings.fundCode(), settings.fundName()) : RoundUp.NONE;
    }
}
