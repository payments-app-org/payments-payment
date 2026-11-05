package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.platform.error.DomainException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// По номеру карты: наша карта — зачисляем на неё, чужая — перевод в другой банк. Номер храним только маской.
@Component
class CardTransferPreparer implements PaymentPreparer {

    private final AccountClient account;

    CardTransferPreparer(AccountClient account) {
        this.account = account;
    }

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.CARD2CARD);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(CardNumbers.valid(req.recipientCardNumber()), CARD_FORMAT);
        require(req.recipientPhone() == null && req.recipientBankId() == null, PHONE_NOT_NEEDED);
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        String card = ctx.request().recipientCardNumber();
        try {
            return Prepared.builder(ctx).recipient(account.findRecipientByCard(card))
                    .recipientCardMask(CardNumbers.mask(card)).build();
        } catch (RestClientException e) {
            throw DomainException.withCause(ACCOUNT_UNAVAILABLE, e);
        }
    }
}
