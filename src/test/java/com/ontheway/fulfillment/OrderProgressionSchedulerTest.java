package com.ontheway.fulfillment;

import com.ontheway.model.Order;
import com.ontheway.model.OrderEvent;
import com.ontheway.model.Payment;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.repository.OrderEventRepository;
import com.ontheway.repository.OrderRepository;
import com.ontheway.repository.PaymentRepository;
import com.ontheway.realtime.OrderRealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProgressionSchedulerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderEventRepository orderEventRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRealtimeNotifier realtimeNotifier;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    private OrderProgressionScheduler scheduler() {
        return new OrderProgressionScheduler(orderRepository, orderEventRepository, paymentRepository, realtimeNotifier, clock);
    }

    @Test
    void advancesDueAcceptedOrdersToPreparing_andAudits() {
        Order due = Order.builder().orderId(1L).status(OrderStatus.ACCEPTED)
                .prepStartAt(LocalDateTime.of(2026, 1, 1, 11, 59)).build();
        when(orderRepository.findByStatusAndPrepStartAtLessThanEqual(eq(OrderStatus.ACCEPTED), any()))
                .thenReturn(List.of(due));
        when(paymentRepository.findByOrderOrderId(1L)).thenReturn(Optional.of(Payment.builder()
                .paymentStatus(PaymentStatus.COMPLETED).build()));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        int advanced = scheduler().advanceDueOrders();

        assertThat(advanced).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(OrderStatus.PREPARING);

        ArgumentCaptor<OrderEvent> ev = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(ev.capture());
        assertThat(ev.getValue().getFromStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(ev.getValue().getToStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(ev.getValue().getChangedBy()).isEqualTo("system:scheduler");
        verify(realtimeNotifier).publish("ORDER_STATUS_CHANGED", due);
    }

    @Test
    void doesNothingWhenNoOrdersAreDue() {
        when(orderRepository.findByStatusAndPrepStartAtLessThanEqual(eq(OrderStatus.ACCEPTED), any()))
                .thenReturn(List.of());

        int advanced = scheduler().advanceDueOrders();

        assertThat(advanced).isZero();
        verify(orderRepository, never()).save(any());
        verify(orderEventRepository, never()).save(any());
        verifyNoInteractions(realtimeNotifier);
    }
}
