package com.ontheway.service;

import com.ontheway.dto.*;

import java.util.List;

public interface OrderService {
    OrderResponseDTO placeOrder(Long userId, OrderCreateDTO dto);
    OrderResponseDTO getOrderById(Long orderId, String callerEmail);
    List<OrderResponseDTO> getOrdersByUser(Long userId);
    List<OrderResponseDTO> getOrdersByMerchant(Long merchantId);

    /** All orders across every shop owned by the merchant identified by email. */
    List<OrderResponseDTO> getOrdersForOwner(String ownerEmail);

    OrderResponseDTO updateOrderStatus(Long orderId, String status, String callerEmail);

    /**
     * Records the customer's current position for an active order and recomputes the live ETA
     * (ready time, prep start, and the traffic-aware arrival window). Only the order's owner may
     * call this, and only while the order has not yet been picked up or cancelled.
     */
    EtaQuoteResponse updateLiveLocation(Long orderId, double latitude, double longitude, String callerEmail);
}
