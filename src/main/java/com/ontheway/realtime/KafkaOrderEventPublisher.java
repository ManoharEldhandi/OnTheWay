package com.ontheway.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ontheway.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaOrderEventPublisher {
    private final KafkaTemplate<String, OrderRealtimeEvent> kafkaTemplate;

    @Value("${ontheway.messaging.kafka.topic:ontheway.order-events}")
    private String topic;

    public void publish(OrderRealtimeEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.orderId()), event);
    }
}
