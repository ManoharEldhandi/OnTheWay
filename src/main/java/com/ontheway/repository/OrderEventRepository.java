package com.ontheway.repository;

import com.ontheway.model.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderOrderIdOrderByCreatedAtAsc(Long orderId);
}
