package com.paymentsapp.payment.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
interface ProcessingApi {

    @PostExchange("/api/processing/transfers")
    ProcessingClient.TransferResult transfer(@RequestBody TransferRequest request);

    @GetExchange("/api/processing/transfers/{paymentId}")
    ProcessingClient.TransferResult result(@PathVariable UUID paymentId);

    @GetExchange("/api/processing/sbp/banks")
    SbpBanks sbpBanks(@RequestParam("phone") String phone);

    // Номер карты получателя передаём только здесь, в базе payment его нет. Поля за рубеж — только для ABROAD.
    record TransferRequest(UUID paymentId, String type, BigDecimal amount, String recipientPhone, String recipientBankId,
                           String recipientCardNumber, String country, String receiveCurrency, BigDecimal receiveAmount,
                           String transferMethod) {
    }

    record SbpBanks(String phone, List<String> banks) {
    }
}
