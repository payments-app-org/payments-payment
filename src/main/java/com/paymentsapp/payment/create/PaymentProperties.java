package com.paymentsapp.payment.create;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Минимальная сумма любого платежа
@ConfigurationProperties("payment")
public record PaymentProperties(BigDecimal minAmount) {
}
