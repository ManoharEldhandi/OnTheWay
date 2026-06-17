package com.ontheway.realtime;

import com.ontheway.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderRealtimeNotifier {
    private final OrderWebSocketHandler handler;

    public void publish(String type, Order order) {
        handler.broadcast(new OrderRealtimeEvent(
                type,
                order.getOrderId(),
                order.getUser().getUserId(),
                order.getMerchant().getMerchantId(),
                order.getMerchant().getUser().getUserId(),
                order.getStatus(),
                order.getPickupTime(),
                order.getEtaSegment()
        ));
    }
}
