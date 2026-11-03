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
    // Списание с привязанной карты другого банка — его проводит процессинг
    private boolean sourceExternal;
    // Зачисление на привязанную карту другого банка — его тоже проводит процессинг
    private boolean destinationExternal;
    private String senderName;
    private UUID recipientUserId;
    private UUID recipientAccountId;
    private String recipientName;
    private String recipientPhone;
    private String recipientBankId;
    private String recipientCardMask;
    private BigDecimal amount;
    private BigDecimal commissionAmount;
    private Integer commissionTariffId;
    private BigDecimal total;
    private String currency;
    private UUID quoteId;
    private String message;
    private String processingId;
    // За рубеж
    private String country;
    private String receiveCurrency;
    private BigDecimal receiveAmount;
    private BigDecimal fxRate;
    private Integer transferMethodId;
    private String transferMethodName;

    // Данные получателя по полям способа перевода; номера карт — маской
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String recipientDetails;

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
        this(userId, type, sourceAccountId, Parties.external(null), recipientPhone, recipientBankId, recipientCardMask,
                amount, commissionAmount, commissionTariffId, currency, quoteId, null);
    }

    public Payment(UUID userId, PaymentType type, UUID sourceAccountId, Parties parties, String recipientPhone,
                   String recipientBankId, String recipientCardMask, BigDecimal amount, BigDecimal commissionAmount,
                   int commissionTariffId, String currency, UUID quoteId, String message) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.status = PaymentStatus.CREATED;
        this.sourceAccountId = sourceAccountId;
        this.sourceExternal = parties.sourceExternal();
        this.destinationExternal = parties.destinationExternal();
        this.senderName = parties.senderName();
        this.recipientUserId = parties.recipientUserId();
        this.recipientAccountId = parties.recipientAccountId();
        this.recipientName = parties.recipientName();
        this.recipientPhone = recipientPhone;
        this.recipientBankId = recipientBankId;
        this.recipientCardMask = recipientCardMask;
        this.amount = amount;
        this.commissionAmount = commissionAmount;
        this.commissionTariffId = commissionTariffId;
        this.total = amount.add(commissionAmount);
        this.currency = currency;
        this.quoteId = quoteId;
        this.message = message;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    // Кто платит и кому. Получатель-клиент нашего банка — есть userId и карта для зачисления.
    // sourceExternal / destinationExternal — списываем с / зачисляем на привязанную карту другого банка.
    public record Parties(String senderName, UUID recipientUserId, UUID recipientAccountId, String recipientName,
                          boolean sourceExternal, boolean destinationExternal) {

        public Parties(String senderName, UUID recipientUserId, UUID recipientAccountId, String recipientName) {
            this(senderName, recipientUserId, recipientAccountId, recipientName, false, false);
        }

        public Parties(String senderName, UUID recipientUserId, UUID recipientAccountId, String recipientName,
                       boolean sourceExternal) {
            this(senderName, recipientUserId, recipientAccountId, recipientName, sourceExternal, false);
        }

        public static Parties external(String senderName) {
            return new Parties(senderName, null, null, null);
        }

        public Parties withExternal(boolean source, boolean destination) {
            return new Parties(senderName, recipientUserId, recipientAccountId, recipientName, source, destination);
        }
    }

    // Детали перевода за рубеж
    public record Abroad(String country, String receiveCurrency, BigDecimal receiveAmount, BigDecimal fxRate,
                         int transferMethodId, String transferMethodName, String recipientDetailsJson) {
    }

    public void setAbroad(Abroad a) {
        this.country = a.country();
        this.receiveCurrency = a.receiveCurrency();
        this.receiveAmount = a.receiveAmount();
        this.fxRate = a.fxRate();
        this.transferMethodId = a.transferMethodId();
        this.transferMethodName = a.transferMethodName();
        this.recipientDetails = a.recipientDetailsJson();
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
    public boolean isSourceExternal() { return sourceExternal; }
    public boolean isDestinationExternal() { return destinationExternal; }

    // Деньги не покидают банк — можно без антифрода и процессинга
    public boolean staysInBank() {
        return type == PaymentType.OWN_TRANSFER && !sourceExternal && !destinationExternal;
    }
    public String getSenderName() { return senderName; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public UUID getRecipientAccountId() { return recipientAccountId; }
    public String getRecipientName() { return recipientName; }
    public String getMessage() { return message; }
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getCountry() { return country; }
    public String getReceiveCurrency() { return receiveCurrency; }
    public BigDecimal getReceiveAmount() { return receiveAmount; }
    public BigDecimal getFxRate() { return fxRate; }
    public Integer getTransferMethodId() { return transferMethodId; }
    public String getTransferMethodName() { return transferMethodName; }
    public String getRecipientDetails() { return recipientDetails; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
