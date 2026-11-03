package com.paymentsapp.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {
}
