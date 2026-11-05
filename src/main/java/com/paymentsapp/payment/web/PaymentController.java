package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.error.PaymentError.PAYMENT_NOT_FOUND;
import static com.paymentsapp.payment.error.PaymentError.SBP_DIRECTORY_UNAVAILABLE;

import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.create.PaymentRequest;
import com.paymentsapp.payment.create.PaymentService;
import com.paymentsapp.payment.create.PaymentService.Outcome;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.payment.PaymentRepository;
import com.paymentsapp.platform.error.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

// Авторизации пока нет: пользователя передаёт фронтенд в X-User-Id. Логика приёма платежа — PaymentService.
@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    static final String USER_HEADER = "X-User-Id";
    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final int KEY_MIN = 8;
    private static final int KEY_MAX = 100;

    private final PaymentService payments;
    private final PaymentRepository repository;
    private final ProcessingClient processing;

    public PaymentController(PaymentService payments, PaymentRepository repository, ProcessingClient processing) {
        this.payments = payments;
        this.repository = repository;
        this.processing = processing;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(USER_HEADER) UUID userId,
                                    @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = KEY_MIN, max = KEY_MAX) String key,
                                    @Valid @RequestBody PaymentRequest req) {
        return switch (payments.submit(userId, key, req)) {
            case Outcome.Done done -> respond(done.payment());
            case Outcome.StoredProblem stored -> ResponseEntity.status(stored.status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(stored.body());
        };
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> get(@RequestHeader(USER_HEADER) UUID userId, @PathVariable UUID paymentId) {
        Payment p = repository.findById(paymentId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new DomainException(PAYMENT_NOT_FOUND));
        return respond(p);
    }

    // Справочник СБП для формы: в каких банках у владельца телефона есть счёт (перевод возможен только туда)
    @GetMapping("/sbp/banks")
    public SbpBanks sbpBanks(@RequestParam String phone) {
        try {
            return new SbpBanks(phone, processing.sbpBanks(phone));
        } catch (RestClientException e) {
            throw DomainException.withCause(SBP_DIRECTORY_UNAVAILABLE, e);
        }
    }

    private static ResponseEntity<PaymentResponse> respond(Payment p) {
        PaymentResponse body = PaymentResponse.of(p);
        return ResponseEntity.status(body.httpStatus()).body(body);
    }

    public record SbpBanks(String phone, List<String> banks) {
    }
}
