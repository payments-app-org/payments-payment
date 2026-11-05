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

    enum GatewayStatus {
        ACCEPTED,
        REJECTED
    }

    record GatewayResult(UUID paymentId, GatewayStatus status, String externalId, String voucherCode, String message) {

        ProcessingClient.TransferResult toTransferResult() {
            boolean accepted = status == GatewayStatus.ACCEPTED;
            return new ProcessingClient.TransferResult(accepted ? ProcessingClient.TransferStatus.COMPLETED
                    : ProcessingClient.TransferStatus.DECLINED, externalId, accepted ? null : message);
        }
    }
}
