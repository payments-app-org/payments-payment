package com.paymentsapp.payment.client;

import com.paymentsapp.payment.payment.Payment;
import com.paymentsapp.platform.resilience.ResilientCalls;
import org.springframework.stereotype.Component;

// Антифрод: проверка идемпотентна по paymentId — повтор при сбое связи безопасен
@Component
public class FraudClient {

    private final FraudApi api;
    private final ResilientCalls calls;

    FraudClient(FraudApi api, ResilientCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    public FraudDecision check(Payment p) {
        FraudApi.CheckRequest request = new FraudApi.CheckRequest(p.getId(), p.getUserId(), p.getType().name(), p.getAmount());
        return calls.call(Backends.FRAUD, () -> api.check(request));
    }

    public enum Verdict {
        APPROVED,
        REJECTED
    }

    public record FraudDecision(Verdict decision, String reason) {

        public boolean approved() {
            return decision == Verdict.APPROVED;
        }
    }
}
