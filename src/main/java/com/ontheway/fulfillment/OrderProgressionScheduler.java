package com.ontheway.fulfillment;

import com.ontheway.model.Order;
import com.ontheway.model.OrderEvent;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.repository.OrderEventRepository;
import com.ontheway.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Makes the ETA promise self-driving: automatically moves an order from
 * {@code PLACED} to {@code PREPARING} once its computed {@code prepStartAt} arrives — so the
 * store starts cooking at exactly the right moment without anyone clicking.
 *
 * <p>The scan logic is extracted into {@link #advanceDueOrders()} so it can be unit-tested
 * deterministically with an injected {@link Clock}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProgressionScheduler {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final Clock clock;

    /** Runs every 30 seconds in the background. */
    @Scheduled(fixedDelayString = "${ontheway.eta.scheduler-interval-ms:30000}")
    public void tick() {
        int advanced = advanceDueOrders();
        if (advanced > 0) {
            log.info("Auto-advanced {} order(s) to PREPARING.", advanced);
        }
    }

    /**
     * Moves every PLACED order whose {@code prepStartAt} has arrived into PREPARING,
     * recording an audit event. Returns the number of orders advanced.
     */
    @Transactional
    public int advanceDueOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Order> due = orderRepository
                .findByStatusAndPrepStartAtLessThanEqual(OrderStatus.PLACED, now);

        for (Order order : due) {
            if (order.getStatus().canTransitionTo(OrderStatus.PREPARING)) {
                order.setStatus(OrderStatus.PREPARING);
                orderRepository.save(order);
                orderEventRepository.save(OrderEvent.builder()
                        .order(order)
                        .fromStatus(OrderStatus.PLACED)
                        .toStatus(OrderStatus.PREPARING)
                        .changedBy("system:scheduler")
                        .reason("Auto-started preparation at scheduled prep time")
                        .build());
            }
        }
        return due.size();
    }
}
