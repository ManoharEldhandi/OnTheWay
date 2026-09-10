package com.ontheway.service;

import com.ontheway.dto.OrderMessageResponseDTO;

import java.util.List;

public interface OrderMessageService {
    List<OrderMessageResponseDTO> list(Long orderId, String callerEmail);
    OrderMessageResponseDTO send(Long orderId, String body, String callerEmail);
}
