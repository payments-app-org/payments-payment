package com.paymentsapp.payment.web;

import static com.paymentsapp.payment.web.PaymentException.Code.*;

import com.paymentsapp.payment.client.AccountClient;
import com.paymentsapp.payment.client.AccountClient.Recipient;
import com.paymentsapp.payment.client.BillerClient;
import com.paymentsapp.payment.client.ProcessingClient;
import com.paymentsapp.payment.idempotency.Idempotency;
import com.paymentsapp.payment.idempotency.IdempotencyKey;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.payment.payment.PaymentRepository;
import com.paymentsapp.payment.payment.PaymentSaga;
import com.paymentsapp.payment.payment.PaymentStore;
import com.paymentsapp.payment.payment.PaymentType;
import com.paymentsapp.payment.quote.Quote;
import com.paymentsapp.payment.quote.QuoteVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
import tools.jackson.databind.json.JsonMapper;

// Авторизации пока нет: пользователя передаёт фронтенд в X-User-Id
@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private final Idempotency idempotency;
    private final QuoteVerifier quotes;
    private final PaymentStore store;
    private final PaymentRepository payments;
    private final PaymentSaga saga;
    private final AccountClient account;
    private final BillerClient biller;
    private final ProcessingClient processing;
    private final JsonMapper json;

    public PaymentController(Idempotency idempotency, QuoteVerifier quotes, PaymentStore store,
                             PaymentRepository payments, PaymentSaga saga, AccountClient account, BillerClient biller,
                             ProcessingClient processing, JsonMapper json) {
        this.idempotency = idempotency;
        this.quotes = quotes;
        this.store = store;
        this.payments = payments;
        this.saga = saga;
        this.account = account;
        this.biller = biller;
        this.processing = processing;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("X-User-Id") UUID userId,
                                    @RequestHeader("X-Idempotency-Key") @Size(min = 8, max = 100) String key,
                                    @Valid @RequestBody PaymentRequest req) {
        // Шаг 2.1. Захват ключа идемпотентности до любой другой работы
        Optional<IdempotencyKey> previous = idempotency.acquire(key, userId, hash(userId, req));
        if (previous.isPresent()) {
            return replay(previous.get(), userId, req);
        }

        Payment payment;
        String cardForProcessing = req.recipientCardNumber();
        try {
            // Шаг 2.2–2.4. Котировка, реквизиты, создание платежа — до внешних вызовов, чтобы остался след
            // Тариф зависел от получателя: карта (перевод по карте) или свой счёт зачисления
            // Оплата поставщику: тариф зависел от поставщика (его группы)
            String recipientKey = req.type() == PaymentType.OWN_TRANSFER && req.destinationAccountId() != null
                    ? req.destinationAccountId().toString()
                    : req.type() == PaymentType.BILL ? req.providerCode() : req.recipientCardNumber();
            Quote quote = quotes.verify(req.quoteToken(), userId, req.sourceAccountId(), req.type().name(), req.amount(),
                    recipientKey);
            Requisites.validate(req);
            AbroadDetails.Checked abroad = req.type() == PaymentType.ABROAD ? checkAbroad(req, quote) : null;
            if (abroad != null) {
                cardForProcessing = abroad.cardNumber();
            }
            payment = createPayment(userId, req, quote, key, abroad);
        } catch (PaymentException e) {
            if (e.getCode() == SERVICE_UNAVAILABLE) {
                idempotency.release(key);
            } else {
                idempotency.saveResponse(key, e.getStatus().value(), json.writeValueAsString(problem(e)));
            }
            throw e;
        }

        // Шаги 3–6
        return respond(saga.run(payment, cardForProcessing));
    }

    // Справочник СБП для формы: в каких банках у владельца телефона есть счёт (перевод возможен только туда)
    @GetMapping("/sbp/banks")
    public java.util.Map<String, Object> sbpBanks(@RequestParam String phone) {
        try {
            return java.util.Map.of("phone", phone, "banks", processing.sbpBanks(phone));
        } catch (RestClientException e) {
            throw new PaymentException(SERVICE_UNAVAILABLE, "Не удалось проверить банк получателя, попробуйте позже");
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> get(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID paymentId) {
        Payment p = payments.findById(paymentId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND, "Платёж не найден"));
        return respond(p);
    }

    private AbroadDetails.Checked checkAbroad(PaymentRequest req, Quote quote) {
        try {
            var method = biller.method(req.transferMethodId())
                    .orElseThrow(() -> new PaymentException(INVALID_REQUISITES, "Способ перевода не найден"));
            return AbroadDetails.check(req, quote, method, json);
        } catch (RestClientException e) {
            throw new PaymentException(SERVICE_UNAVAILABLE, "Справочник переводов недоступен, попробуйте позже");
        }
    }

    private Payment createPayment(UUID userId, PaymentRequest req, Quote quote, String key, AbroadDetails.Checked abroad) {
        Optional<Recipient> recipient;
        String senderName;
        boolean sourceExternal;
        boolean destinationExternal = false;
        try {
            AccountClient.UserInfo user = account.user(userId);
            sourceExternal = user.isExternal(req.sourceAccountId());
            if (req.type() == PaymentType.OWN_TRANSFER) {
                // Стороны — свои счета: в истории будет «С Зарплатная ·· 3752» / «На Мой кошелёк»
                senderName = "С " + user.title(req.sourceAccountId(), "своего счёта");
                destinationExternal = user.isExternal(req.destinationAccountId());
                recipient = Optional.of(new Recipient(userId, "На " + user.title(req.destinationAccountId(), "свой счёт"),
                        req.destinationAccountId(), null));
            } else if (req.type() == PaymentType.WALLET_TRANSFER) {
                // На «Мой кошелёк» другого клиента: ищем его по телефону
                senderName = user.displayName();
                Recipient r = account.findRecipientByPhone(req.recipientPhone())
                        .filter(x -> x.walletAccountId() != null)
                        .orElseThrow(() -> new PaymentException(RECIPIENT_NOT_FOUND, "Кошелёк с таким номером не найден"));
                if (r.userId().equals(userId)) {
                    throw new PaymentException(RECIPIENT_NOT_FOUND, "Это ваш кошелёк — пополните его переводом между своими счетами");
                }
                recipient = Optional.of(new Recipient(r.userId(), r.displayName(), r.walletAccountId(), null));
            } else if (req.type() != PaymentType.SBP_PHONE && req.type() != PaymentType.CARD2CARD) {
                // За рубеж, юрлицу, в бюджет — получатель не наш клиент
                // Получатель за рубежом — не наш клиент
                senderName = user.displayName();
                recipient = Optional.empty();
            } else {
                senderName = user.displayName();
                recipient = req.type() == PaymentType.SBP_PHONE
                        ? account.findRecipientByPhone(req.recipientPhone())
                        : account.findRecipientByCard(req.recipientCardNumber());
            }
        } catch (RestClientException e) {
            throw new PaymentException(SERVICE_UNAVAILABLE, "Сервис счетов недоступен, попробуйте позже");
        }

        // Банк из списка — берём его; не выбран — банк по умолчанию получателя
        String bankId = req.recipientBankId();
        if (req.type() == PaymentType.SBP_PHONE && bankId == null) {
            bankId = recipient.map(Recipient::sbpBankId).orElseThrow(() -> new PaymentException(RECIPIENT_BANK_UNKNOWN,
                    "Не удалось определить банк получателя, выберите банк из списка"));
        }
        // СБП: у получателя должен быть счёт в выбранном банке (справочник НСПК — у процессинга)
        if (req.type() == PaymentType.SBP_PHONE) {
            java.util.List<String> banks;
            try {
                banks = processing.sbpBanks(req.recipientPhone());
            } catch (RestClientException e) {
                throw new PaymentException(SERVICE_UNAVAILABLE, "Не удалось проверить банк получателя, попробуйте позже");
            }
            if (!banks.contains(bankId)) {
                throw new PaymentException(RECIPIENT_BANK_NOT_FOUND, "У получателя нет счёта в этом банке");
            }
        }

        // Юрлицу и в бюджет: проверенные реквизиты, имя получателя и назначение платежа
        RuRequisites.Checked requisites = null;
        String billReference = null;
        BillerClient.BillCheck bill = null;
        if (req.type() == PaymentType.REQUISITES || req.type() == PaymentType.REQUISITES_OUR) {
            requisites = RuRequisites.check(req.recipientFields(), req.type() == PaymentType.REQUISITES_OUR, json);
        } else if (req.type() == PaymentType.BILL) {
            // Всё про поставщика проверяет biller: поля, сумму, номинал, оператора по номеру, начисление
            try {
                bill = biller.checkBill(req.providerCode(), req.recipientFields(), req.amount(), req.chargeId());
            } catch (RestClientException e) {
                throw new PaymentException(SERVICE_UNAVAILABLE, "Справочник поставщиков недоступен, попробуйте позже");
            }
            requisites = new RuRequisites.Checked(bill.requisites() == null ? null : json.writeValueAsString(bill.requisites()),
                    bill.recipientName(), bill.purpose());
            billReference = bill.billReference();
            if (billReference != null && payments.existsByBillReferenceAndStatusNot(billReference,
                    com.paymentsapp.payment.payment.PaymentStatus.FAILED)) {
                throw new PaymentException(BILL_NOT_PAYABLE, "Это начисление уже оплачено или оплачивается");
            }
        }
        final RuRequisites.Checked payee = requisites;

        Payment.Parties parties = recipient
                .map(r -> new Payment.Parties(senderName, r.userId(), r.accountId(), r.displayName()))
                .orElse(abroad != null ? new Payment.Parties(senderName, null, null, abroad.recipientName())
                        : payee != null ? new Payment.Parties(senderName, null, null, payee.recipientName())
                        : Payment.Parties.external(senderName))
                .withExternal(sourceExternal, destinationExternal);

        String recipientPhone = bill != null ? bill.recipientPhone() : req.recipientPhone();
        Payment payment = new Payment(userId, req.type(), req.sourceAccountId(), parties, recipientPhone, bankId,
                req.recipientCardNumber() == null ? null : Requisites.maskCard(req.recipientCardNumber()),
                req.amount(), quote.commission(), quote.tariffId(), req.currencyOrDefault(), quote.quoteId(),
                req.message() == null || req.message().isBlank() ? null : req.message().trim());
        if (abroad != null) {
            payment.setAbroad(abroad.abroad());
        }
        if (payee != null) {
            payment.setRequisites(payee.json(), payee.purpose(), billReference);
        }
        if (bill != null) {
            payment.setBill(bill.providerCode(), bill.providerGroup(), json.writeValueAsString(bill.fields()));
        }
        try {
            return store.create(payment, key);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains("payments_bill_once_uq")) {
                throw new PaymentException(BILL_NOT_PAYABLE, "Это начисление уже оплачено или оплачивается");
            }
            // Уникальный индекс по quote_id: по этой котировке уже платили
            throw new PaymentException(QUOTE_ALREADY_USED, "Эта котировка уже использована, пересчитайте комиссию");
        }
    }

    // Повтор с тем же ключом: тот же ответ, без повторного списания
    private ResponseEntity<?> replay(IdempotencyKey previous, UUID userId, PaymentRequest req) {
        if (!previous.sameRequest(userId, hash(userId, req))) {
            throw new PaymentException(IDEMPOTENCY_KEY_REUSED, "Ключ идемпотентности уже использован для другого запроса");
        }
        if (previous.getResponseStatus() != null) {
            return ResponseEntity.status(previous.getResponseStatus())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(previous.getResponseBody());
        }
        if (previous.getPaymentId() != null) {
            return respond(store.get(previous.getPaymentId()));
        }
        throw new PaymentException(PAYMENT_IN_PROGRESS, "Запрос с этим ключом ещё обрабатывается");
    }

    private ResponseEntity<PaymentResponse> respond(Payment p) {
        PaymentResponse body = PaymentResponse.of(p);
        return ResponseEntity.status(body.httpStatus()).body(body);
    }

    static ProblemDetail problem(PaymentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        problem.setProperty("code", e.getCode().name());
        return problem;
    }

    private String hash(UUID userId, PaymentRequest req) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + json.writeValueAsString(req)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
