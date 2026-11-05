package com.paymentsapp.payment.web;

import com.paymentsapp.payment.payment.AbroadDetails;
import com.paymentsapp.payment.payment.FailureCode;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.payment.PaymentStatus;
import com.paymentsapp.payment.payment.PaymentType;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record PaymentResponse(
        UUID paymentId,
        PaymentType type,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal commission,
        BigDecimal total,
        String recipientName,
        String recipientBankId,
        String receiveCurrency,
        BigDecimal receiveAmount,
        FailureCode failureCode,
        String message,
        // «Округление на пользу»: сколько добавили к покупке и в какой фонд
        BigDecimal roundUpAmount,
        String roundUpFundName
) {

    private static final String DONE = "Перевод выполнен";
    private static final String RETURNING = ". Возвращаем средства";
    private static final String IN_PROGRESS = "Перевод обрабатывается";

    static PaymentResponse of(Payment p) {
        String message = switch (p.getStatus()) {
            case SUCCESS -> DONE;
            case FAILED -> p.getFailureReason();
            case COMPENSATING -> p.getFailureReason() + RETURNING;
            default -> IN_PROGRESS;
        };
        AbroadDetails abroad = p.getAbroad();
        return new PaymentResponse(p.getId(), p.getType(), p.getStatus(), p.getAmount(), p.getCommissionAmount(),
                p.getTotal(), p.getRecipientName(), p.getRecipientBankId(), abroad == null ? null : abroad.receiveCurrency(),
                abroad == null ? null : abroad.receiveAmount(), p.getFailureCode(), message, p.getRoundUp().amount(),
                p.getRoundUp().fundName());
    }

    // 200 — платёж завершён (успешно или нет), 202 — ещё в процессе
    HttpStatus httpStatus() {
        return status.isFinal() ? HttpStatus.OK : HttpStatus.ACCEPTED;
    }
}
