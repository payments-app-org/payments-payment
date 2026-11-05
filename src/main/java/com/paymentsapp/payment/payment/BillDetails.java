package com.paymentsapp.payment.payment;

import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Оплата поставщику (BILL): кто поставщик, его группа и что ввёл плательщик — [{key, label, value, display}]
@Embeddable
public record BillDetails(String providerCode, String providerGroup, @JdbcTypeCode(SqlTypes.JSON) String billFields) {
}
