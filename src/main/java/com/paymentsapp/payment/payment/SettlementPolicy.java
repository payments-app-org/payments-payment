package com.paymentsapp.payment.payment;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Группы поставщиков, которым biller переводит деньги сам, одним переводом в конце дня (пожертвования фондам):
// у таких платежей нет шага «оплата у поставщика» — после антифрода сразу списание
@ConfigurationProperties("payment.settlement")
public record SettlementPolicy(Set<String> deferredGroups) {

    public boolean isDeferred(Payment p) {
        return p.getType() == PaymentType.BILL && p.getBill() != null && deferredGroups.contains(p.getBill().providerGroup());
    }
}
