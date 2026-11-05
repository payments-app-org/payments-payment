package com.paymentsapp.payment.create;

import com.paymentsapp.payment.payment.PaymentType;
import java.util.Set;

// Подготовка платежа своего вида: проверка полей запроса, получатель, реквизиты. Новый вид платежа — новый класс,
// PaymentService и контроллер не меняются.
public interface PaymentPreparer {

    Set<PaymentType> types();

    // Поля запроса этого вида — до походов к соседям
    void validate(PaymentRequest request);

    // Реквизит, на который pricing считал тариф (его хэш — в котировке): по умолчанию номер карты получателя
    default String quoteRecipientKey(PaymentRequest request) {
        return request.recipientCardNumber();
    }

    Prepared prepare(PreparationContext context);
}
