package com.paymentsapp.payment.quote;

import static com.paymentsapp.payment.web.PaymentException.Code.*;

import com.paymentsapp.payment.web.PaymentException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public QuoteVerifier(JsonMapper json, @Value("${quote.secret}") String secret) {
        this(json, secret, Clock.systemUTC());
    }

    QuoteVerifier(JsonMapper json, String secret, Clock clock) {
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    // recipientKey — реквизит, на который платим (номер карты); должен совпасть с тем, для которого считали тариф
    public Quote verify(String token, UUID userId, UUID sourceAccountId, String operationType, BigDecimal amount,
                        String recipientKey) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2) {
            throw new PaymentException(INVALID_QUOTE, "Некорректная котировка");
        }
        byte[] signature;
        Quote quote;
        try {
            signature = Base64.getUrlDecoder().decode(parts[1]);
            quote = json.readValue(Base64.getUrlDecoder().decode(parts[0]), Quote.class);
        } catch (IllegalArgumentException | JacksonException e) {
            throw new PaymentException(INVALID_QUOTE, "Некорректная котировка");
        }
        if (!MessageDigest.isEqual(signature, hmac(parts[0]))) {
            throw new PaymentException(INVALID_QUOTE, "Подпись котировки не сходится");
        }
        if (!Instant.now(clock).isBefore(quote.expiresAt())) {
            throw new PaymentException(QUOTE_EXPIRED, "Расчёт комиссии устарел, обновите сумму");
        }
        if (!quote.userId().equals(userId) || !quote.sourceAccountId().equals(sourceAccountId)
                || !quote.operationType().equals(operationType) || quote.amount().compareTo(amount) != 0
                || quote.recipientHash() != null && !quote.recipientHash().equals(sha256(recipientKey))) {
            throw new PaymentException(QUOTE_MISMATCH, "Котировка выдана на другой платёж");
        }
        return quote;
    }

    private static String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
