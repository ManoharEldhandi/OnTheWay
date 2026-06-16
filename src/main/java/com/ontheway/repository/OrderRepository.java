package com.ontheway.repository;

import com.ontheway.model.Order;
import com.ontheway.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserUserId(Long userId);
    List<Order> findByMerchantMerchantId(Long merchantId);

    /** Orders in a given status whose prep-start time is at or before the cutoff. */
    List<Order> findByStatusAndPrepStartAtLessThanEqual(OrderStatus status, LocalDateTime cutoff);
}
