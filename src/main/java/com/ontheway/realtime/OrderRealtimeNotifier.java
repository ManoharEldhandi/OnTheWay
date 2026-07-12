package com.ontheway.realtime;

import com.ontheway.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderRealtimeNotifier {
    private final OrderWebSocketHandler handler;
    private final ObjectProvider<KafkaOrderEventPublisher> kafkaPublisher;

    public void publish(String type, Order order) {
        OrderRealtimeEvent event = new OrderRealtimeEvent(
                type,
                order.getOrderId(),
                order.getUser().getUserId(),
                order.getMerchant().getMerchantId(),
                order.getMerchant().getUser().getUserId(),
                order.getStatus(),
                order.getPickupTime(),
                order.getEtaSegment()
        );
        handler.broadcast(event);
        kafkaPublisher.ifAvailable(publisher -> publisher.publish(event));
    }
}
