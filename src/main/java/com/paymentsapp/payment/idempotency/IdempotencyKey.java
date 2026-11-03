package com.paymentsapp.payment.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    private String key;

    private UUID userId;
    private String requestHash;
    private UUID paymentId;
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    private OffsetDateTime createdAt;

    protected IdempotencyKey() {
    }

    public boolean sameRequest(UUID userId, String requestHash) {
        return this.userId.equals(userId) && this.requestHash.equals(requestHash);
    }

    void linkPayment(UUID paymentId) {
        this.paymentId = paymentId;
    }

    void saveResponse(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    public UUID getPaymentId() { return paymentId; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
}
