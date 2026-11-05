package com.paymentsapp.payment.quote;

import static com.paymentsapp.payment.error.PaymentError.*;

import com.paymentsapp.platform.crypto.Hashing;
import com.paymentsapp.platform.error.DomainException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Проверка котировки локально, без похода в pricing: подпись, срок, и что она выдана на этот же платёж
@Component
public class QuoteVerifier {

    private final JsonMapper json;
    private final byte[] secret;
    private final Clock clock;

    public QuoteVerifier(JsonMapper json, @Value("${quote.secret}") String secret, Clock clock) {
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    // recipientKey — реквизит, на который платим (номер карты); должен совпасть с тем, для которого считали тариф
    public Quote verify(String token, UUID userId, UUID sourceAccountId, String operationType, BigDecimal amount,
                        String recipientKey) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2) {
            throw new DomainException(INVALID_QUOTE);
        }
        byte[] signature;
        Quote quote;
        try {
            signature = Base64.getUrlDecoder().decode(parts[1]);
            quote = json.readValue(Base64.getUrlDecoder().decode(parts[0]), Quote.class);
        } catch (IllegalArgumentException | JacksonException e) {
            throw new DomainException(INVALID_QUOTE);
        }
        if (!MessageDigest.isEqual(signature, hmac(parts[0]))) {
            throw new DomainException(QUOTE_SIGNATURE);
        }
        if (!Instant.now(clock).isBefore(quote.expiresAt())) {
            throw new DomainException(QUOTE_EXPIRED);
        }
        if (!quote.userId().equals(userId) || !quote.sourceAccountId().equals(sourceAccountId)
                || !quote.operationType().equals(operationType) || quote.amount().compareTo(amount) != 0
                || quote.recipientHash() != null && !quote.recipientHash().equals(sha256(recipientKey))) {
            throw new DomainException(QUOTE_MISMATCH);
        }
        return quote;
    }

    private static String sha256(String value) {
        return value == null ? null : Hashing.sha256Hex(value);
    }

    private byte[] hmac(String data) {
        return Hashing.hmacSha256(secret, data.getBytes(StandardCharsets.US_ASCII));
    }
}
