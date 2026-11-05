package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.PaymentType;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

// Между своими счетами: обе стороны — счета плательщика. Тариф pricing считал на счёт зачисления.
@Component
class OwnTransferPreparer implements PaymentPreparer {

    // Как стороны видны в истории: «С Зарплатная ·· 3752» / «На Мой кошелёк»
    private static final String FROM = "С %s";
    private static final String TO = "На %s";
    private static final String OWN_ACCOUNT = "своего счёта";
    private static final String OWN_DESTINATION = "свой счёт";

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.OWN_TRANSFER);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(req.destinationAccountId() != null, DESTINATION_REQUIRED);
        require(!req.destinationAccountId().equals(req.sourceAccountId()), SAME_ACCOUNT);
        require(req.recipientPhone() == null && req.recipientBankId() == null && req.recipientCardNumber() == null,
                OWN_REQUISITES_NOT_NEEDED);
    }

    @Override
    public String quoteRecipientKey(PaymentRequest req) {
        return req.destinationAccountId().toString();
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        PaymentRequest req = ctx.request();
        AccountClient.UserInfo user = ctx.user();
        AccountClient.Recipient self = new AccountClient.Recipient(ctx.userId(),
                TO.formatted(user.title(req.destinationAccountId(), OWN_DESTINATION)), req.destinationAccountId(), null, null);
        return Prepared.builder(ctx)
                .senderName(FROM.formatted(user.title(req.sourceAccountId(), OWN_ACCOUNT)))
                .recipient(Optional.of(self))
                .destinationExternal(user.isExternal(req.destinationAccountId()))
                .cardForProcessing(null)
                .build();
    }
}
