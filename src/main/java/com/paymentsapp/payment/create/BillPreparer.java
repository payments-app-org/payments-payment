package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.payment.client.BillerClient;
import com.paymentsapp.payment.payment.BillDetails;
import com.paymentsapp.payment.payment.PaymentRepository;
import com.paymentsapp.payment.payment.PaymentStatus;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.platform.error.DomainException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

// Оплата поставщику: всё про поставщика проверяет biller — поля, сумму, номинал, оператора по номеру, начисление.
// Одно начисление (billReference) — один живой платёж.
@Component
class BillPreparer implements PaymentPreparer {

    private final BillerClient biller;
    private final PaymentRepository payments;
    private final JsonMapper json;

    BillPreparer(BillerClient biller, PaymentRepository payments, JsonMapper json) {
        this.biller = biller;
        this.payments = payments;
        this.json = json;
    }

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.BILL);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(req.providerCode() != null && !req.providerCode().isBlank(), PROVIDER_REQUIRED);
    }

    // Тариф зависел от поставщика (его группы)
    @Override
    public String quoteRecipientKey(PaymentRequest req) {
        return req.providerCode();
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        PaymentRequest req = ctx.request();
        BillerClient.BillCheck bill;
        try {
            bill = biller.checkBill(req.providerCode(), req.recipientFields(), req.amount(), req.chargeId());
        } catch (RestClientException e) {
            throw DomainException.withCause(BILLER_UNAVAILABLE, e);
        }
        if (bill.billReference() != null && payments.existsByBillReferenceAndStatusNot(bill.billReference(), PaymentStatus.FAILED)) {
            throw new DomainException(BILL_NOT_PAYABLE);
        }
        String requisites = bill.requisites() == null ? null : json.writeValueAsString(bill.requisites());
        return Prepared.builder(ctx)
                .recipientName(bill.recipientName())
                .recipientPhone(bill.recipientPhone())
                .payee(new Prepared.Payee(requisites, bill.purpose(), bill.billReference()))
                .bill(new BillDetails(bill.providerCode(), bill.providerGroup(), json.writeValueAsString(bill.fields())))
                .build();
    }
}
