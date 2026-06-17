package com.ontheway.repository;

import com.ontheway.model.Order;
import com.ontheway.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserUserId(Long userId);
    List<Order> findByMerchantMerchantId(Long merchantId);

    /** All orders across every shop owned by a user (the merchant's full order queue). */
    List<Order> findByMerchant_User_UserId(Long ownerUserId);

    Page<Order> findByMerchant_User_UserId(Long ownerUserId, Pageable pageable);

    /** Orders in a given status whose prep-start time is at or before the cutoff. */
    List<Order> findByStatusAndPrepStartAtLessThanEqual(OrderStatus status, LocalDateTime cutoff);

    long countByStatus(OrderStatus status);
}
