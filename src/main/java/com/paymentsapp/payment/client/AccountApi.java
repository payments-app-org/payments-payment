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
interface AccountApi {

    @PostExchange("/api/internal/reservations")
    void reserve(@RequestBody ReserveRequest request);

    @PostExchange("/api/internal/reservations/{paymentId}/capture")
    void capture(@PathVariable UUID paymentId);

    @PostExchange("/api/internal/reservations/{paymentId}/release")
    void release(@PathVariable UUID paymentId);

    @GetExchange("/api/users/{userId}")
    UserProfile user(@PathVariable UUID userId);

    @GetExchange("/api/internal/recipients")
    AccountClient.Recipient recipientByPhone(@RequestParam("phone") String phone);

    @GetExchange("/api/internal/recipients")
    AccountClient.Recipient recipientByCard(@RequestParam("cardNumber") String cardNumber);

    // commission — вместе с округлением на пользу: списать нужно всё вместе
    record ReserveRequest(UUID paymentId, UUID userId, UUID accountId, UUID recipientAccountId, String operationType,
                          BigDecimal amount, BigDecimal commission, String currency, String providerGroup) {
    }

    record UserProfile(String displayName, List<AccountView> accounts, RoundupView roundup) {
    }

    record AccountView(UUID id, String title, AccountKind type, String cardLast4) {
    }

    record RoundupView(int step, String fundCode, String fundName) {
    }
}
