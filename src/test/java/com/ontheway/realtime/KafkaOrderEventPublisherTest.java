package com.ontheway.realtime;

import com.ontheway.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaOrderEventPublisherTest {

    @Test
    void publishesOrderEventToConfiguredTopicKeyedByOrderId() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, OrderRealtimeEvent> template = mock(KafkaTemplate.class);
        KafkaOrderEventPublisher publisher = new KafkaOrderEventPublisher(template);
        ReflectionTestUtils.setField(publisher, "topic", "ontheway.order-events");
        OrderRealtimeEvent event = new OrderRealtimeEvent(
                "ORDER_STATUS_CHANGED", 42L, 1L, 2L, 3L,
                OrderStatus.PREPARING, null, "ON_TIME");

        publisher.publish(event);

        verify(template).send("ontheway.order-events", "42", event);
    }
}
