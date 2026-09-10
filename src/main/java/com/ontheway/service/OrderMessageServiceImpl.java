package com.ontheway.service.impl;

import com.ontheway.dto.OrderMessageResponseDTO;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.Order;
import com.ontheway.model.OrderMessage;
import com.ontheway.model.User;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.realtime.OrderRealtimeNotifier;
import com.ontheway.repository.OrderMessageRepository;
import com.ontheway.repository.OrderRepository;
import com.ontheway.repository.UserRepository;
import com.ontheway.service.OrderMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Deliberately small order chat: only the customer and serving merchant, only while active. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderMessageServiceImpl implements OrderMessageService {
    private final OrderRepository orderRepository;
    private final OrderMessageRepository orderMessageRepository;
    private final UserRepository userRepository;
    private final OrderRealtimeNotifier realtimeNotifier;

    @Override
    public List<OrderMessageResponseDTO> list(Long orderId, String callerEmail) {
        Order order = findOrder(orderId);
        assertParticipant(order, findUser(callerEmail));
        return orderMessageRepository.findByOrderOrderIdOrderByCreatedAtAsc(orderId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    @Override
    public OrderMessageResponseDTO send(Long orderId, String body, String callerEmail) {
        Order order = findOrder(orderId);
        User sender = findUser(callerEmail);
        assertParticipant(order, sender);
        if (!chatIsOpen(order.getStatus())) {
            throw new BadRequestException("Order chat opens after acceptance and closes at hand-off");
        }

        OrderMessage message = orderMessageRepository.save(OrderMessage.builder()
                .order(order)
                .sender(sender)
                .body(body.trim())
                .build());
        realtimeNotifier.publish("ORDER_CHAT_MESSAGE", order);
        return toResponse(message);
    }

    private boolean chatIsOpen(OrderStatus status) {
        return status == OrderStatus.ACCEPTED
                || status == OrderStatus.PREPARING
                || status == OrderStatus.READY;
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private void assertParticipant(Order order, User caller) {
        boolean customer = order.getUser().getUserId().equals(caller.getUserId());
        boolean merchant = order.getMerchant().getUser().getUserId().equals(caller.getUserId());
        if (!customer && !merchant) {
            throw new ForbiddenException("Only the customer and serving merchant can use this order chat");
        }
    }

    private OrderMessageResponseDTO toResponse(OrderMessage message) {
        return OrderMessageResponseDTO.builder()
                .orderMessageId(message.getOrderMessageId())
                .orderId(message.getOrder().getOrderId())
                .senderUserId(message.getSender().getUserId())
                .senderName(message.getSender().getName())
                .senderRole(message.getSender().getRole().name())
                .body(message.getBody())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
