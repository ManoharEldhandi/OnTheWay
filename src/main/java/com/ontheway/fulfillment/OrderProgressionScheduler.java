package com.ontheway.fulfillment;

import com.ontheway.model.Order;
import com.ontheway.model.OrderEvent;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.repository.OrderEventRepository;
import com.ontheway.repository.OrderRepository;
import com.ontheway.repository.PaymentRepository;
import com.ontheway.realtime.OrderRealtimeNotifier;
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
 * {@code ACCEPTED} to {@code PREPARING} once its computed {@code prepStartAt} arrives — so the
 * store starts cooking at exactly the right moment, but only after the shop has accepted it.
 *
 * <p>The scan logic is extracted into {@link #advanceDueOrders()} so it can be unit-tested
 * deterministically with an injected {@link Clock}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderProgressionScheduler {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRealtimeNotifier realtimeNotifier;
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
     * Moves every ACCEPTED order whose {@code prepStartAt} has arrived into PREPARING,
     * recording an audit event. Returns the number of orders advanced.
     */
    public int advanceDueOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Order> due = orderRepository
                .findByStatusAndPrepStartAtLessThanEqual(OrderStatus.ACCEPTED, now);

        int advanced = 0;
        for (Order order : due) {
            boolean paid = paymentRepository.findByOrderOrderId(order.getOrderId())
                    .map(payment -> payment.getPaymentStatus() == PaymentStatus.COMPLETED)
                    .orElse(false);
            if (paid && order.getStatus().canTransitionTo(OrderStatus.PREPARING)) {
                order.setStatus(OrderStatus.PREPARING);
                orderRepository.save(order);
                orderEventRepository.save(OrderEvent.builder()
                        .order(order)
                        .fromStatus(OrderStatus.ACCEPTED)
                        .toStatus(OrderStatus.PREPARING)
                        .changedBy("system:scheduler")
                        .reason("Auto-started preparation at scheduled prep time")
                        .build());
                realtimeNotifier.publish("ORDER_STATUS_CHANGED", order);
                advanced++;
            }
        }
        return advanced;
    }
}
