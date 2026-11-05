package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.platform.error.DomainException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// На «Мой кошелёк» другого клиента по номеру телефона. На свой кошелёк — «между своими».
@Component
class WalletTransferPreparer implements PaymentPreparer {

    private final AccountClient account;

    WalletTransferPreparer(AccountClient account) {
        this.account = account;
    }

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.WALLET_TRANSFER);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(Formats.phone(req.recipientPhone()), PHONE_FORMAT);
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        AccountClient.Recipient owner;
        try {
            owner = account.findRecipientByPhone(ctx.request().recipientPhone())
                    .filter(r -> r.walletAccountId() != null)
                    .orElseThrow(() -> new DomainException(WALLET_NOT_FOUND));
        } catch (RestClientException e) {
            throw DomainException.withCause(ACCOUNT_UNAVAILABLE, e);
        }
        require(!owner.userId().equals(ctx.userId()), OWN_WALLET);
        AccountClient.Recipient wallet = new AccountClient.Recipient(owner.userId(), owner.displayName(),
                owner.walletAccountId(), null, null);
        return Prepared.builder(ctx).recipient(Optional.of(wallet)).build();
    }
}
