package com.paymentsapp.payment.create;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.payment.AbroadDetails;
import com.paymentsapp.payment.payment.BillDetails;
import com.paymentsapp.payment.payment.Payment;
import java.util.Optional;

// Результат подготовки: стороны платежа, реквизиты для хранения и номер карты для процессинга (в базу не попадает)
public record Prepared(Payment.Parties parties, String recipientPhone, String recipientBankId, String recipientCardMask,
                       Payee payee, BillDetails bill, AbroadDetails abroad, String cardForProcessing) {

    // Реквизиты юрлица / бюджета / получателя за рубежом и назначение платежа
    public record Payee(String detailsJson, String purpose, String billReference) {
    }

    static Builder builder(PreparationContext ctx) {
        return new Builder(ctx);
    }

    static final class Builder {
        private final PreparationContext ctx;
        private String senderName;
        private Optional<AccountClient.Recipient> recipient = Optional.empty();
        private String recipientName;
        private boolean destinationExternal;
        private String recipientPhone;
        private String recipientBankId;
        private String recipientCardMask;
        private Payee payee;
        private BillDetails bill;
        private AbroadDetails abroad;
        private String cardForProcessing;

        private Builder(PreparationContext ctx) {
            this.ctx = ctx;
            this.senderName = ctx.user().displayName();
            this.recipientPhone = ctx.request().recipientPhone();
            this.recipientBankId = ctx.request().recipientBankId();
            this.cardForProcessing = ctx.request().recipientCardNumber();
        }

        Builder senderName(String name) { this.senderName = name; return this; }
        Builder recipient(Optional<AccountClient.Recipient> r) { this.recipient = r; return this; }
        Builder recipientName(String name) { this.recipientName = name; return this; }
        Builder destinationExternal(boolean external) { this.destinationExternal = external; return this; }
        Builder recipientPhone(String phone) { this.recipientPhone = phone; return this; }
        Builder recipientBankId(String bankId) { this.recipientBankId = bankId; return this; }
        Builder recipientCardMask(String mask) { this.recipientCardMask = mask; return this; }
        Builder payee(Payee p) { this.payee = p; return this; }
        Builder bill(BillDetails b) { this.bill = b; return this; }
        Builder abroad(AbroadDetails a) { this.abroad = a; return this; }
        Builder cardForProcessing(String card) { this.cardForProcessing = card; return this; }

        Prepared build() {
            Payment.Parties parties = recipient
                    .map(r -> new Payment.Parties(senderName, r.userId(), r.accountId(), r.displayName()))
                    .orElse(new Payment.Parties(senderName, null, null, recipientName))
                    .withExternal(ctx.sourceExternal(), destinationExternal);
            return new Prepared(parties, recipientPhone, recipientBankId, recipientCardMask, payee, bill, abroad, cardForProcessing);
        }
    }
}
