package com.paymentsapp.payment.create;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.payment.payment.RoundUp;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoundUpPolicyTest {

    private final RoundUpPolicy policy = new RoundUpPolicy(Set.of(PaymentType.REQUISITES), Set.of("MOBILE"));
    private final AccountClient.Roundup tens = new AccountClient.Roundup(10, "dari-edu", "Дари еду");

    @Test
    void roundsEligiblePaymentsUpToStep() {
        RoundUp r = policy.of(PaymentType.BILL, "MOBILE", new BigDecimal("85"), tens);
        assertThat(r.amount()).isEqualByComparingTo("5.00");
        assertThat(r.fundName()).isEqualTo("Дари еду");
        assertThat(policy.of(PaymentType.REQUISITES, null, new BigDecimal("1234.50"), tens).amount()).isEqualByComparingTo("5.50");
    }

    @Test
    void skipsTransfersOtherGroupsExactAmountsAndDisabledSetting() {
        assertThat(policy.of(PaymentType.SBP_PHONE, null, new BigDecimal("85"), tens)).isEqualTo(RoundUp.NONE);
        assertThat(policy.of(PaymentType.BILL, "TRANSPORT", new BigDecimal("85"), tens)).isEqualTo(RoundUp.NONE);
        assertThat(policy.of(PaymentType.BILL, "MOBILE", new BigDecimal("90"), tens)).isEqualTo(RoundUp.NONE);
        assertThat(policy.of(PaymentType.BILL, "MOBILE", new BigDecimal("85"), null)).isEqualTo(RoundUp.NONE);
    }
}
