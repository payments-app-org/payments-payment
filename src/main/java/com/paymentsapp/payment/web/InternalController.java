package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.error.PaymentError.RESULT_RETRY_LATER;

import com.paymentsapp.payment.client.ProcessingClient.TransferResult;
import com.paymentsapp.payment.client.ProcessingClient.TransferStatus;
import com.paymentsapp.payment.incoming.IncomingSbpService;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.payment.PaymentSaga;
import com.paymentsapp.platform.error.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Внутренний API для процессинга (внешней системы): итоги долгих переводов и входящие переводы по СБП.
// Процессинг — чужая система, в нашу Kafka его не пускают: он вызывает нас по HTTP и повторяет запрос,
// пока не получит 2xx. Поэтому оба метода идемпотентны.
@RestController
@RequestMapping("/api/internal")
public class InternalController {

    private static final String PHONE = "\\+7\\d{10}";
    private static final String BANK_ID = "\\d{12}";
    private static final int MONEY_INTEGER = 15;
    private static final int MONEY_FRACTION = 2;
    private static final String MIN_AMOUNT = "0.01";
    private static final int ID_MAX = 100;
    private static final int NAME_MAX = 100;
    private static final int MESSAGE_MAX = 40;

    private final PaymentSaga saga;
    private final IncomingSbpService incoming;

    public InternalController(PaymentSaga saga, IncomingSbpService incoming) {
        this.saga = saga;
        this.incoming = incoming;
    }

    // Итог долгого перевода. Опоздавший или повторный итог — 200 без изменений.
    @PostMapping("/processing/results")
    public ResponseEntity<Void> processingResult(@Valid @RequestBody ResultRequest req) {
        try {
            saga.onProcessingResult(req.paymentId(), new TransferResult(req.status(), req.externalId(), req.reason()));
        } catch (ObjectOptimisticLockingFailureException e) {
            // Платёж в этот момент двигает RecoveryJob — процессинг повторит уведомление
            throw DomainException.withCause(RESULT_RETRY_LATER, e);
        }
        return ResponseEntity.ok().build();
    }

    // Зачисление входящего перевода. 200 — зачислено или отказ (decision), 503 — повторите позже.
    @PostMapping("/sbp/incoming")
    public IncomingResponse incomingSbp(@Valid @RequestBody IncomingRequest req) {
        Payment p = incoming.receive(new IncomingSbpService.Incoming(req.operationId(), req.senderName(), req.senderBankId(),
                req.recipientPhone(), req.amount(), req.message()));
        return switch (p.getStatus()) {
            case SUCCESS -> new IncomingResponse(p.getId(), Decision.CREDITED, null);
            case FAILED -> new IncomingResponse(p.getId(), Decision.DECLINED, p.getFailureReason());
            // Зачисление не удалось из-за сбоя связи с account: СБП повторит запрос, мы — тоже
            default -> throw new DomainException(RESULT_RETRY_LATER);
        };
    }

    public record ResultRequest(
            @NotNull UUID paymentId,
            @NotNull TransferStatus status,
            @Size(max = ID_MAX) String externalId,
            @Size(max = NAME_MAX * 2) String reason) {
    }

    public record IncomingRequest(
            @NotBlank @Size(max = ID_MAX) String operationId,
            @NotBlank @Size(max = NAME_MAX) String senderName,
            @NotNull @Pattern(regexp = BANK_ID, message = "идентификатор банка СБП — 12 цифр") String senderBankId,
            @NotNull @Pattern(regexp = PHONE, message = "телефон в формате +7XXXXXXXXXX") String recipientPhone,
            @NotNull @DecimalMin(MIN_AMOUNT) @Digits(integer = MONEY_INTEGER, fraction = MONEY_FRACTION) BigDecimal amount,
            @Size(max = MESSAGE_MAX) String message) {
    }

    public enum Decision { CREDITED, DECLINED }

    public record IncomingResponse(UUID paymentId, Decision decision, String reason) {
    }
}
