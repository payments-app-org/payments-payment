package com.paymentsapp.payment.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/biller")
interface BillerApi {

    @GetExchange("/methods/{id}")
    BillerClient.MethodDetails method(@PathVariable int id);

    @PostExchange("/bills/check")
    BillerClient.BillCheck check(@RequestBody CheckRequest request);

    @PostExchange("/payments")
    GatewayResult pay(@RequestBody PayRequest request);

    @GetExchange("/payments/{paymentId}")
    GatewayResult payment(@PathVariable UUID paymentId);

    record CheckRequest(String providerCode, Map<String, String> fields, BigDecimal amount, UUID chargeId) {
    }

    record PayRequest(UUID paymentId, String providerCode, BigDecimal amount, Map<String, String> fields) {
    }

    // PENDING — поставщик принял платёж, но проведёт позже (подписка через партнёра)
    enum GatewayStatus {
        ACCEPTED,
        REJECTED,
        PENDING
    }

    record GatewayResult(UUID paymentId, GatewayStatus status, String externalId, String voucherCode, String message) {

        ProcessingClient.TransferResult toTransferResult() {
            return switch (status) {
                case ACCEPTED -> new ProcessingClient.TransferResult(ProcessingClient.TransferStatus.COMPLETED, externalId, null);
                case REJECTED -> new ProcessingClient.TransferResult(ProcessingClient.TransferStatus.DECLINED, externalId, message);
                case PENDING -> new ProcessingClient.TransferResult(ProcessingClient.TransferStatus.IN_PROGRESS, externalId, null);
            };
        }
    }
}
