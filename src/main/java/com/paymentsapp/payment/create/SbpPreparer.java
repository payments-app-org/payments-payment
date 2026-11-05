package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.platform.error.DomainException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

// По номеру телефона через СБП. Банк из списка — он; не выбран — банк по умолчанию получателя-клиента.
// У получателя должен быть счёт в этом банке (справочник НСПК — у процессинга).
@Component
class SbpPreparer implements PaymentPreparer {

    private final AccountClient account;
    private final ProcessingClient processing;

    SbpPreparer(AccountClient account, ProcessingClient processing) {
        this.account = account;
        this.processing = processing;
    }

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.SBP_PHONE);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(Formats.phone(req.recipientPhone()), PHONE_FORMAT);
        require(req.recipientBankId() == null || Formats.SBP_BANK_ID.matcher(req.recipientBankId()).matches(), BANK_FORMAT);
        require(req.recipientCardNumber() == null, CARD_NOT_NEEDED);
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        PaymentRequest req = ctx.request();
        Optional<AccountClient.Recipient> recipient;
        try {
            recipient = account.findRecipientByPhone(req.recipientPhone());
        } catch (RestClientException e) {
            throw DomainException.withCause(ACCOUNT_UNAVAILABLE, e);
        }
        String bankId = req.recipientBankId() != null ? req.recipientBankId()
                : recipient.map(AccountClient.Recipient::sbpBankId).orElseThrow(() -> new DomainException(RECIPIENT_BANK_UNKNOWN));
        List<String> banks;
        try {
            banks = processing.sbpBanks(req.recipientPhone());
        } catch (RestClientException e) {
            throw DomainException.withCause(SBP_DIRECTORY_UNAVAILABLE, e);
        }
        require(banks.contains(bankId), RECIPIENT_BANK_NOT_FOUND);
        return Prepared.builder(ctx).recipient(recipient).recipientBankId(bankId).build();
    }
}
