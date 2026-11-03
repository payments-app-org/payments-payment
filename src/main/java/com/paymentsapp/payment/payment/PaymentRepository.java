package com.paymentsapp.payment.payment;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAt(Collection<PaymentStatus> statuses,
                                                                         OffsetDateTime before);
}
