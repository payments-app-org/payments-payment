package com.paymentsapp.payment.create;

import static com.paymentsapp.payment.create.Checks.require;
import static com.paymentsapp.payment.error.PaymentError.REQUISITES_REQUIRED;

import com.paymentsapp.payment.payment.PaymentType;
import java.util.Set;
import org.springframework.stereotype.Component;

// Юрлицу по реквизитам: в другой банк (REQUISITES) или на счёт в нашем банке (REQUISITES_OUR, БИК подставляем)
@Component
class RequisitesPreparer implements PaymentPreparer {

    private final RuRequisitesValidator validator;

    RequisitesPreparer(RuRequisitesValidator validator) {
        this.validator = validator;
    }

    @Override
    public Set<PaymentType> types() {
        return Set.of(PaymentType.REQUISITES, PaymentType.REQUISITES_OUR);
    }

    @Override
    public void validate(PaymentRequest req) {
        require(req.recipientFields() != null && !req.recipientFields().isEmpty(), REQUISITES_REQUIRED);
    }

    @Override
    public Prepared prepare(PreparationContext ctx) {
        PaymentRequest req = ctx.request();
        RuRequisitesValidator.Checked payee = validator.check(req.recipientFields(), req.type() == PaymentType.REQUISITES_OUR);
        return Prepared.builder(ctx).recipientName(payee.recipientName())
                .payee(new Prepared.Payee(payee.json(), payee.purpose(), null)).build();
    }
}
