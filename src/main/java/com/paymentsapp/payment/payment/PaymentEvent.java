package com.paymentsapp.payment.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Итог платежа в топике payment.events (PaymentSucceeded / PaymentFailed). Пустые поля не отправляем:
// данные BILL — только у оплат поставщику, за рубеж — у переводов за рубеж, округление — если было.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentEvent(
        UUID paymentId, UUID userId, PaymentType type, PaymentStatus status, UUID sourceAccountId, String senderName,
        UUID recipientUserId, UUID recipientAccountId, String recipientName, String recipientPhone, String recipientBankId,
        // Банк, откуда пришли деньги (входящий СБП, пополнение со своего счёта в другом банке)
        String sourceBankId,
        String recipientCardMask, String message, boolean sourceExternal, boolean destinationExternal, String purpose,
        String billReference,
        // Деньги фонду переведёт biller в конце дня (пожертвование), а не шлюз поставщика сразу
        Boolean charitySettlement,
        BigDecimal roundUpAmount, String roundUpFundCode, String roundUpFundName,
        String providerCode, String providerGroup, String billFields,
        String country, String receiveCurrency, BigDecimal receiveAmount, BigDecimal fxRate, String transferMethodName,
        OffsetDateTime createdAt, BigDecimal amount, BigDecimal commission, BigDecimal total, String currency,
        FailureCode failureCode, String failureReason
) {

    static final String SUCCEEDED = "PaymentSucceeded";
    static final String FAILED = "PaymentFailed";

    static PaymentEvent of(Payment p, boolean deferredSettlement) {
        RoundUp r = p.getRoundUp().isPresent() ? p.getRoundUp() : null;
        BillDetails b = p.getBill();
        AbroadDetails a = p.getAbroad();
        return new PaymentEvent(p.getId(), p.getUserId(), p.getType(), p.getStatus(), p.getSourceAccountId(), p.getSenderName(),
                p.getRecipientUserId(), p.getRecipientAccountId(), p.getRecipientName(), p.getRecipientPhone(),
                p.getRecipientBankId(), p.getSourceBankId(), p.getRecipientCardMask(), p.getMessage(), p.isSourceExternal(),
                p.isDestinationExternal(), p.getPurpose(), p.getBillReference(),
                deferredSettlement ? Boolean.TRUE : null,
                r == null ? null : r.amount(), r == null ? null : r.fundCode(), r == null ? null : r.fundName(),
                b == null ? null : b.providerCode(), b == null ? null : b.providerGroup(), b == null ? null : b.billFields(),
                a == null ? null : a.country(), a == null ? null : a.receiveCurrency(), a == null ? null : a.receiveAmount(),
                a == null ? null : a.fxRate(), a == null ? null : a.transferMethodName(),
                p.getCreatedAt(), p.getAmount(), p.getCommissionAmount(), p.getTotal(), p.getCurrency(),
                p.getFailureCode(), p.getFailureReason());
    }

    String eventType() {
        return status == PaymentStatus.SUCCESS ? SUCCEEDED : FAILED;
    }
}
