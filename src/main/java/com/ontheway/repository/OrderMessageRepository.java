package com.ontheway.repository;

import com.ontheway.model.OrderMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderMessageRepository extends JpaRepository<OrderMessage, Long> {
    List<OrderMessage> findByOrderOrderIdOrderByCreatedAtAsc(Long orderId);
}
