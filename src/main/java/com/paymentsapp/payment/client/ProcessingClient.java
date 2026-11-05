package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.AbroadDetails;
import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.platform.resilience.ResilientCalls;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

// Процессинг. Перевод повторяем, только если не удалось соединиться: после тайм-аута исход неизвестен —
// его узнаём запросом статуса (findResult), а не повторной отправкой.
@Component
public class ProcessingClient {

    private final ProcessingApi api;
    private final ResilientCalls calls;

    ProcessingClient(ProcessingApi api, ResilientCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    public TransferResult transfer(Payment p, String recipientCardNumber) {
        AbroadDetails abroad = p.getAbroad();
        ProcessingApi.TransferRequest request = new ProcessingApi.TransferRequest(p.getId(), p.getType().name(), p.getAmount(),
                p.getRecipientPhone(), p.getRecipientBankId(), recipientCardNumber,
                abroad == null ? null : abroad.country(), abroad == null ? null : abroad.receiveCurrency(),
                abroad == null ? null : abroad.receiveAmount(), abroad == null ? null : abroad.transferMethodName());
        return calls.call(Backends.PROCESSING, Backends.PROCESSING_CONNECT_ONLY, () -> api.transfer(request));
    }

    // Чем закончился перевод, если ответ на transfer не дошёл. Пусто — процессинг перевод не получал.
    public Optional<TransferResult> findResult(UUID paymentId) {
        return calls.call(Backends.PROCESSING, () -> {
            try {
                return Optional.ofNullable(api.result(paymentId));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    // Справочник СБП: в каких банках у владельца телефона есть счёт
    public List<String> sbpBanks(String phone) {
        ProcessingApi.SbpBanks banks = calls.call(Backends.PROCESSING, () -> api.sbpBanks(phone));
        return banks == null ? List.of() : banks.banks();
    }

    public enum TransferStatus {
        COMPLETED,
        DECLINED
    }

    public record TransferResult(TransferStatus status, String externalId, String reason) {

        public boolean completed() {
            return status == TransferStatus.COMPLETED;
        }
    }
}
