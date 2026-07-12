package com.ontheway.realtime;

import com.ontheway.model.Merchant;
import com.ontheway.model.Order;
import com.ontheway.model.User;
import com.ontheway.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderRealtimeNotifierTest {

    @Test
    void broadcastsAndForwardsTheSameComplianceTraceableEvent() {
        OrderWebSocketHandler handler = mock(OrderWebSocketHandler.class);
        KafkaOrderEventPublisher kafkaPublisher = mock(KafkaOrderEventPublisher.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("kafkaOrderEventPublisher", kafkaPublisher);
        ObjectProvider<KafkaOrderEventPublisher> provider = beanFactory.getBeanProvider(KafkaOrderEventPublisher.class);
        User customer = User.builder().userId(1L).build();
        User owner = User.builder().userId(2L).build();
        Merchant merchant = Merchant.builder().merchantId(3L).user(owner).build();
        LocalDateTime pickup = LocalDateTime.of(2026, 7, 12, 12, 30);
        Order order = Order.builder()
                .orderId(42L)
                .user(customer)
                .merchant(merchant)
                .status(OrderStatus.READY)
                .pickupTime(pickup)
                .etaSegment("ON_TIME")
                .build();

        new OrderRealtimeNotifier(handler, provider).publish("ORDER_ETA_CHANGED", order);

        verify(handler).broadcast(argThat(event -> event.orderId().equals(42L)
                && event.type().equals("ORDER_ETA_CHANGED")
                && event.status() == OrderStatus.READY));
        verify(kafkaPublisher).publish(argThat(event -> event.orderId().equals(42L)
                && event.pickupTime().equals(pickup)));
    }
}
