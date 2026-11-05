package com.paymentsapp.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

// «Округление на пользу»: сколько добавили к покупке и в какой фонд это уйдёт
@Embeddable
public record RoundUp(@Column(name = "round_up_amount") BigDecimal amount,
                      @Column(name = "round_up_fund_code") String fundCode,
                      @Column(name = "round_up_fund_name") String fundName) {

    public static final RoundUp NONE = new RoundUp(BigDecimal.ZERO, null, null);

    public boolean isPresent() {
        return amount.signum() > 0;
    }
}
