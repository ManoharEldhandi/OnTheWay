package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.fulfillment.EtaCalculation;
import com.ontheway.fulfillment.EtaService;
import com.ontheway.fulfillment.GeoPoint;
import com.ontheway.model.*;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.*;
import com.ontheway.service.OrderService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventRepository orderEventRepository;
    private final EtaService etaService;

    @Transactional
    @Override
    public OrderResponseDTO placeOrder(Long userId, OrderCreateDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Merchant merchant = merchantRepository.findById(dto.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BadRequestException("An order must contain at least one item");
        }

        // ETA synchronization: if the customer shared their live location and the store
        // has a location, compute when the order should be ready (on arrival) and persist
        // a human-readable ETA summary. Otherwise fall back to a client-supplied pickup time.
        LocalDateTime pickupTime = dto.getPickupTime();
        String etaSegment = null;
        boolean canSyncEta = dto.getLatitude() != null && dto.getLongitude() != null
                && merchant.getLatitude() != null && merchant.getLongitude() != null;
        if (canSyncEta) {
            EtaCalculation eta = etaService.estimate(
                    new GeoPoint(dto.getLatitude(), dto.getLongitude()), merchant);
            pickupTime = eta.readyAt();
            int readyInMins = Math.max(0,
                    (int) Duration.between(LocalDateTime.now(), eta.readyAt()).toMinutes());
            etaSegment = String.format(
                    "travel %d min, prep %d min (+%d buffer); ready ~%d min after ordering",
                    eta.travelMins(), eta.prepTimeMins(), eta.bufferMins(), readyInMins);
        }
        if (pickupTime == null) {
            throw new BadRequestException(
                    "Either pickupTime or your current location (latitude/longitude) is required");
        }

        Order order = Order.builder()
                .user(user)
                .merchant(merchant)
                .orderTime(LocalDateTime.now())
                .pickupTime(pickupTime)
                .status(OrderStatus.PLACED)
                .totalAmount(0.0) // Set after items processed
                .etaSegment(etaSegment)
                .build();

        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (OrderItemCreateDTO itemDTO : dto.getItems()) {
            if (itemDTO.getQuantity() == null || itemDTO.getQuantity() < 1) {
                throw new BadRequestException("Item quantity must be at least 1");
            }
            MenuItem item = menuItemRepository.findById(itemDTO.getMenuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Menu item not found: " + itemDTO.getMenuItemId()));

            // Server-authoritative validation: the item must belong to the target
            // merchant and be currently available. Prices come from the catalog, not the client.
            if (!item.getMerchant().getMerchantId().equals(merchant.getMerchantId())) {
                throw new BadRequestException(
                        "Menu item " + item.getMenuItemId() + " does not belong to merchant "
                                + merchant.getMerchantId());
            }
            if (Boolean.FALSE.equals(item.getAvailability())) {
                throw new BadRequestException("Menu item is not available: " + item.getName());
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(item)
                    .quantity(itemDTO.getQuantity())
                    .priceEach(item.getPrice())
                    .build();
            items.add(orderItem);
            total += item.getPrice() * itemDTO.getQuantity();
        }
        order.setItems(items);
        order.setTotalAmount(total);

        orderRepository.save(order);
        recordEvent(order, null, OrderStatus.PLACED, user.getEmail(), "Order placed");
        return toResponseDTO(order);
    }

    @Override
    public OrderResponseDTO getOrderById(Long orderId, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        assertCanView(order, resolveCaller(callerEmail));
        return toResponseDTO(order);
    }

    @Override
    public List<OrderResponseDTO> getOrdersByUser(Long userId) {
        return orderRepository.findByUserUserId(userId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    public List<OrderResponseDTO> getOrdersByMerchant(Long merchantId) {
        return orderRepository.findByMerchantMerchantId(merchantId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public OrderResponseDTO updateOrderStatus(Long orderId, String status, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User caller = resolveCaller(callerEmail);
        assertCanManage(order, caller);

        OrderStatus target = parseStatus(status);
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new BadRequestException(
                    "Illegal status transition: " + current + " -> " + target);
        }

        order.setStatus(target);
        orderRepository.save(order);
        recordEvent(order, current, target, caller.getEmail(), "Status updated");
        return toResponseDTO(order);
    }

    // ----- helpers -------------------------------------------------------

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadRequestException("Unknown order status: " + status);
        }
    }

    private User resolveCaller(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    /** A user may view an order if they own it, serve it (merchant), or are an admin. */
    private void assertCanView(Order order, User caller) {
        boolean isOwner = order.getUser().getUserId().equals(caller.getUserId());
        boolean isServingMerchant = isServingMerchant(order, caller);
        boolean isAdmin = caller.getRole() == UserRole.ADMIN;
        if (!(isOwner || isServingMerchant || isAdmin)) {
            throw new ForbiddenException("You are not allowed to access this order");
        }
    }

    /** Only the serving merchant or an admin may change an order's status. */
    private void assertCanManage(Order order, User caller) {
        if (!(isServingMerchant(order, caller) || caller.getRole() == UserRole.ADMIN)) {
            throw new ForbiddenException("You are not allowed to manage this order");
        }
    }

    private boolean isServingMerchant(Order order, User caller) {
        return caller.getRole() == UserRole.MERCHANT
                && order.getMerchant().getUser().getUserId().equals(caller.getUserId());
    }

    private void recordEvent(Order order, OrderStatus from, OrderStatus to,
                             String changedBy, String reason) {
        orderEventRepository.save(OrderEvent.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(changedBy)
                .reason(reason)
                .build());
    }

    private OrderResponseDTO toResponseDTO(Order order) {
        List<OrderItemResponseDTO> items = order.getItems().stream().map(oi ->
                OrderItemResponseDTO.builder()
                        .orderItemId(oi.getOrderItemId())
                        .menuItemId(oi.getMenuItem().getMenuItemId())
                        .quantity(oi.getQuantity())
                        .priceEach(oi.getPriceEach())
                        .totalPrice(oi.getQuantity() * oi.getPriceEach())
                        .build()
        ).collect(Collectors.toList());

        return OrderResponseDTO.builder()
                .orderId(order.getOrderId())
                .userId(order.getUser().getUserId())
                .merchantId(order.getMerchant().getMerchantId())
                .orderTime(order.getOrderTime())
                .pickupTime(order.getPickupTime())
                .etaSegment(order.getEtaSegment())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(items)
                .payment(order.getPayment() != null ? PaymentResponseDTO.builder()
                        .paymentId(order.getPayment().getPaymentId())
                        .orderId(order.getPayment().getOrder().getOrderId())
                        .paymentStatus(order.getPayment().getPaymentStatus())
                        .paymentMethod(order.getPayment().getPaymentMethod())
                        .amount(order.getPayment().getAmount())
                        .paymentTime(order.getPayment().getPaymentTime())
                        .build() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
