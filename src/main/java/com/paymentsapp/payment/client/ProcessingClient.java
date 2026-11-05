package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.AbroadDetails;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

// Процессинг. Перевод повторяем, только если не удалось соединиться: после тайм-аута исход неизвестен —
// его узнаём запросом статуса (findResult), а не повторной отправкой.
@Component
public class ProcessingClient {

    private static final String EMPTY_ANSWER = "пустой ответ";

    private final ProcessingApi api;
    private final ResilientCalls calls;

    ProcessingClient(ProcessingApi api, ResilientCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    public TransferResult transfer(Payment p, String recipientCardNumber) {
        AbroadDetails abroad = p.getAbroad();
        ProcessingApi.TransferRequest request = new ProcessingApi.TransferRequest(p.getId(), p.getType().name(), p.getAmount(),
                p.getSourceBankId(), p.getRecipientPhone(), p.getRecipientBankId(), recipientCardNumber,
                abroad == null ? null : abroad.country(), abroad == null ? null : abroad.receiveCurrency(),
                abroad == null ? null : abroad.receiveAmount(), abroad == null ? null : abroad.transferMethodName());
        return calls.call(Backends.PROCESSING, Backends.PROCESSING_CONNECT_ONLY, () -> api.transfer(request));
    }

    // Чем закончился перевод: ответ на transfer не дошёл или перевод долгий. 404 — процессинг перевод не получал;
    // сбой связи, 5xx, разомкнутый circuit breaker — узнать не удалось.
    public ResultLookup findResult(UUID paymentId) {
        try {
            return calls.call(Backends.PROCESSING, () -> {
                try {
                    TransferResult result = api.result(paymentId);
                    return result == null ? new ResultLookup.Unknown(EMPTY_ANSWER) : new ResultLookup.Found(result);
                } catch (HttpClientErrorException.NotFound e) {
                    return new ResultLookup.NotReceived();
                }
            });
        } catch (RestClientException e) {
            return new ResultLookup.Unknown(e.getMessage());
        }
    }

    // Справочник СБП: в каких банках у владельца телефона есть счёт
    public List<String> sbpBanks(String phone) {
        ProcessingApi.SbpBanks banks = calls.call(Backends.PROCESSING, () -> api.sbpBanks(phone));
        return banks == null ? List.of() : banks.banks();
    }

    // IN_PROGRESS — перевод принят, итог будет позже (процессинг сообщит сам, а мы спрашиваем на всякий случай)
    public enum TransferStatus {
        COMPLETED,
        DECLINED,
        IN_PROGRESS
    }

    public record TransferResult(TransferStatus status, String externalId, String reason) {

        public boolean completed() {
            return status == TransferStatus.COMPLETED;
        }

        public boolean inProgress() {
            return status == TransferStatus.IN_PROGRESS;
        }
    }
}
