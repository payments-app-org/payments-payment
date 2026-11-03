package com.paymentsapp.payment.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private UUID sourceAccountId;
    private String recipientPhone;
    private String recipientBankId;
    private String recipientCardMask;
    private BigDecimal amount;
    private BigDecimal commissionAmount;
    private Integer commissionTariffId;
    private BigDecimal total;
    private String currency;
    private UUID quoteId;
    private String processingId;

    @Enumerated(EnumType.STRING)
    private FailureCode failureCode;

    private String failureReason;

    // Защита от гонки между запросом пользователя и планировщиком восстановления
    @Version
    private long version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected Payment() {
    }

    public Payment(UUID userId, PaymentType type, UUID sourceAccountId, String recipientPhone, String recipientBankId,
                   String recipientCardMask, BigDecimal amount, BigDecimal commissionAmount, int commissionTariffId,
                   String currency, UUID quoteId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.status = PaymentStatus.CREATED;
        this.sourceAccountId = sourceAccountId;
        this.recipientPhone = recipientPhone;
        this.recipientBankId = recipientBankId;
        this.recipientCardMask = recipientCardMask;
        this.amount = amount;
        this.commissionAmount = commissionAmount;
        this.commissionTariffId = commissionTariffId;
        this.total = amount.add(commissionAmount);
        this.currency = currency;
        this.quoteId = quoteId;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    void moveTo(PaymentStatus to) {
        if (!status.next().contains(to)) {
            throw new IllegalStateException("Платёж " + id + ": переход " + status + " → " + to + " запрещён");
        }
        this.status = to;
        this.updatedAt = OffsetDateTime.now();
    }

    void fail(FailureCode code, String reason) {
        this.failureCode = code;
        this.failureReason = reason;
    }

    void setProcessingId(String processingId) {
        this.processingId = processingId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public PaymentType getType() { return type; }
    public PaymentStatus getStatus() { return status; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientBankId() { return recipientBankId; }
    public String getRecipientCardMask() { return recipientCardMask; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public Integer getCommissionTariffId() { return commissionTariffId; }
    public BigDecimal getTotal() { return total; }
    public String getCurrency() { return currency; }
    public UUID getQuoteId() { return quoteId; }
    public String getProcessingId() { return processingId; }
    public FailureCode getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
