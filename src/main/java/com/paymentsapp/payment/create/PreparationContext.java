package com.paymentsapp.payment.create;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.quote.Quote;
import java.util.UUID;

// Что известно до подготовки: кто платит, запрос, проверенная котировка и профиль плательщика из account
public record PreparationContext(UUID userId, PaymentRequest request, Quote quote, AccountClient.UserInfo user) {

    public boolean sourceExternal() {
        return user.isExternal(request.sourceAccountId());
    }
}
