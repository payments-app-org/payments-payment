package com.paymentsapp.payment.payment;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

// Перевод за рубеж: страна, валюта и сумма к получению, курс, способ перевода
@Embeddable
public record AbroadDetails(String country, String receiveCurrency, BigDecimal receiveAmount, BigDecimal fxRate,
                            Integer transferMethodId, String transferMethodName) {
}
