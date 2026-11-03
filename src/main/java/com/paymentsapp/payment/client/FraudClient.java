package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.Payment;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FraudClient {

    private final RestClient rest;

    public FraudClient(RestClient.Builder builder,
                       @Value("${clients.fraud.url}") String url,
                       @Value("${clients.fraud.timeout}") Duration timeout) {
        this.rest = RestClients.create(builder, url, timeout);
    }

    public FraudDecision check(Payment p) {
        return rest.post().uri("/api/fraud/check")
                .body(Map.of(
                        "paymentId", p.getId(),
                        "userId", p.getUserId(),
                        "type", p.getType().name(),
                        "amount", p.getAmount()))
                .retrieve()
                .body(FraudDecision.class);
    }

    public record FraudDecision(String decision, String reason) {

        public boolean approved() {
            return "APPROVED".equals(decision);
        }
    }
}
