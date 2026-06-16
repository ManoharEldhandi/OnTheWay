package com.ontheway.service;

import com.ontheway.dto.*;

import java.util.List;

public interface OrderService {
    OrderResponseDTO placeOrder(Long userId, OrderCreateDTO dto);
    OrderResponseDTO getOrderById(Long orderId, String callerEmail);
    List<OrderResponseDTO> getOrdersByUser(Long userId);
    List<OrderResponseDTO> getOrdersByMerchant(Long merchantId);
    OrderResponseDTO updateOrderStatus(Long orderId, String status, String callerEmail);
}
