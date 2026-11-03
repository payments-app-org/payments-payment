package com.paymentsapp.payment.payment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_status_history")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID paymentId;
    private String status;
    private String note;
    private OffsetDateTime createdAt;

    protected StatusChange() {
    }

    StatusChange(UUID paymentId, PaymentStatus status, String note) {
        this.paymentId = paymentId;
        this.status = status.name();
        this.note = note == null || note.length() <= 255 ? note : note.substring(0, 255);
        this.createdAt = OffsetDateTime.now();
    }
}
