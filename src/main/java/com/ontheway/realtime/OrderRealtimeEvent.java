package com.ontheway.realtime;

import com.ontheway.model.enums.OrderStatus;

import java.time.LocalDateTime;

public record OrderRealtimeEvent(
        String type,
        Long orderId,
        Long userId,
        Long merchantId,
        Long merchantOwnerId,
        OrderStatus status,
        LocalDateTime pickupTime,
        String etaSegment
) {
}
