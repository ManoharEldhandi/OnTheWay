package com.ontheway.repository;

import com.ontheway.model.Order;
import com.ontheway.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    List<Order> findByUserUserId(Long userId);
    List<Order> findByMerchantMerchantId(Long merchantId);
    boolean existsByUserUserId(Long userId);
    boolean existsByMerchantMerchantId(Long merchantId);

    /** All orders across every shop owned by a user (the merchant's full order queue). */
    List<Order> findByMerchant_User_UserId(Long ownerUserId);

    Page<Order> findByMerchant_User_UserId(Long ownerUserId, Pageable pageable);

    /** Orders in a given status whose prep-start time is at or before the cutoff. */
    @EntityGraph(attributePaths = {"user", "merchant", "merchant.user"})
    List<Order> findByStatusAndPrepStartAtLessThanEqual(OrderStatus status, LocalDateTime cutoff);

    long countByStatus(OrderStatus status);
}
