package com.paymentsapp.payment.payment;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByBillReferenceAndStatusNot(String billReference, PaymentStatus status);

    // Зависшие платежи для RecoveryJob — пачкой не больше limit
    List<Payment> findByStatusInAndUpdatedAtBeforeOrderByUpdatedAt(Collection<PaymentStatus> statuses, OffsetDateTime before,
                                                                   Limit limit);

    Optional<Payment> findByTypeAndProcessingId(PaymentType type, String processingId);
}
