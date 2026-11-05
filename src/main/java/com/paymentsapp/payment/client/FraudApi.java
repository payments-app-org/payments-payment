package com.paymentsapp.payment.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
interface FraudApi {

    @PostExchange("/api/fraud/check")
    FraudClient.FraudDecision check(@RequestBody CheckRequest request);

    record CheckRequest(UUID paymentId, UUID userId, String type, BigDecimal amount) {
    }
}
