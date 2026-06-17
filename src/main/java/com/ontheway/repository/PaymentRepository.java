package com.ontheway.repository;

import com.ontheway.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderOrderId(Long orderId);

    /** Total value of completed payments (platform gross revenue). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentStatus = "
            + "com.ontheway.model.enums.PaymentStatus.COMPLETED")
    double sumCompletedRevenue();
}
