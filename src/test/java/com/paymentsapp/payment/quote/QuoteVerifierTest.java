package com.paymentsapp.payment.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paymentsapp.payment.web.PaymentException;
import com.paymentsapp.payment.web.PaymentException.Code;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class QuoteVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final String SECRET = "test-secret";

    private final JsonMapper json = JsonMapper.builder().build();
    private final QuoteVerifier verifier = new QuoteVerifier(json, SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final Quote quote = new Quote(UUID.randomUUID(), userId, accountId, "SBP_PHONE",
            new BigDecimal("100"), new BigDecimal("1.00"), 1, NOW.plusSeconds(600));

    // Так же, как подписывает pricing
    private String sign(Quote q, String secret) throws Exception {
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(q));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return body + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
    }

    private Code codeOf(Runnable r) {
        try {
            r.run();
        } catch (PaymentException e) {
            return e.getCode();
        }
        throw new AssertionError("ожидали PaymentException");
    }

    @Test
    void acceptsValidQuote() throws Exception {
        Quote verified = verifier.verify(sign(quote, SECRET), userId, accountId, "SBP_PHONE", new BigDecimal("100.00"));

        assertThat(verified.commission()).isEqualByComparingTo("1.00");
    }

    @Test
    void rejectsForeignSignature() throws Exception {
        String token = sign(quote, "other-secret");

        assertThat(codeOf(() -> verifier.verify(token, userId, accountId, "SBP_PHONE", new BigDecimal("100"))))
                .isEqualTo(Code.INVALID_QUOTE);
    }

    @Test
    void rejectsExpiredQuote() throws Exception {
        Quote expired = new Quote(quote.quoteId(), userId, accountId, "SBP_PHONE", new BigDecimal("100"),
                new BigDecimal("1.00"), 1, NOW);
        String token = sign(expired, SECRET);

        assertThat(codeOf(() -> verifier.verify(token, userId, accountId, "SBP_PHONE", new BigDecimal("100"))))
                .isEqualTo(Code.QUOTE_EXPIRED);
    }

    @Test
    void rejectsQuoteForAnotherAmountOrAccount() throws Exception {
        String token = sign(quote, SECRET);

        assertThat(codeOf(() -> verifier.verify(token, userId, accountId, "SBP_PHONE", new BigDecimal("1000"))))
                .isEqualTo(Code.QUOTE_MISMATCH);
        assertThat(codeOf(() -> verifier.verify(token, userId, UUID.randomUUID(), "SBP_PHONE", new BigDecimal("100"))))
                .isEqualTo(Code.QUOTE_MISMATCH);
        assertThat(codeOf(() -> verifier.verify(token, userId, accountId, "CARD2CARD", new BigDecimal("100"))))
                .isEqualTo(Code.QUOTE_MISMATCH);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> verifier.verify("not-a-token", userId, accountId, "SBP_PHONE", BigDecimal.TEN))
                .isInstanceOf(PaymentException.class);
    }
}
