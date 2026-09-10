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
import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.realtime.OrderRealtimeNotifier;
import com.ontheway.repository.*;
import com.ontheway.service.OrderService;
import com.ontheway.service.PaymentService;
import com.ontheway.util.Money;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventRepository orderEventRepository;
    private final LocationRepository locationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final EtaService etaService;
    private final OrderRealtimeNotifier realtimeNotifier;

    private static final SecureRandom PICKUP_CODE_RANDOM = new SecureRandom();

    @Transactional
    @Override
    public OrderResponseDTO placeOrder(Long userId, OrderCreateDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Merchant merchant = merchantRepository.findById(dto.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        // Orders may only be placed at an approved, active shop.
        if (merchant.getStatus() != com.ontheway.model.enums.MerchantStatus.APPROVED) {
            throw new BadRequestException("This shop is not currently accepting orders");
        }

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BadRequestException("An order must contain at least one item");
        }

        // ETA synchronization: if the customer shared their live location and the store
        // has a location, compute when the order should be ready (on arrival) and persist
        // a human-readable ETA summary. Otherwise fall back to a client-supplied pickup time.
        if ((dto.getLatitude() == null) != (dto.getLongitude() == null)) {
            throw new BadRequestException("Latitude and longitude must be provided together");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pickupTime = dto.getPickupTime();
        LocalDateTime prepStartAt = null;
        String etaSegment = null;
        boolean canSyncEta = dto.getLatitude() != null && dto.getLongitude() != null
                && merchant.getLatitude() != null && merchant.getLongitude() != null;
        if (canSyncEta) {
            EtaCalculation eta = etaService.estimate(
                    new GeoPoint(dto.getLatitude(), dto.getLongitude()), merchant);
            pickupTime = eta.readyAt();
            prepStartAt = eta.prepStartAt();
            int readyInMins = Math.max(0,
                    (int) Duration.between(now, eta.readyAt()).toMinutes());
            etaSegment = String.format(
                    "travel %d min, prep %d min (+%d buffer); ready ~%d min after ordering",
                    eta.travelMins(), eta.prepTimeMins(), eta.bufferMins(), readyInMins);
        }
        if (pickupTime == null) {
            throw new BadRequestException(
                    "Either pickupTime or your current location (latitude/longitude) is required");
        }
        if (pickupTime.isBefore(now)) {
            throw new BadRequestException("pickupTime cannot be in the past");
        }

        Order order = Order.builder()
                .user(user)
                .merchant(merchant)
                .orderTime(now)
                .pickupTime(pickupTime)
                .prepStartAt(prepStartAt)
                .customerLatitude(dto.getLatitude())
                .customerLongitude(dto.getLongitude())
                .status(OrderStatus.PLACED)
                .totalAmount(0.0) // Set after items processed
                .totalAmountMinor(0L)
                .currency(Money.DEFAULT_CURRENCY)
                .etaSegment(etaSegment)
                .pickupCode(String.format("%06d", PICKUP_CODE_RANDOM.nextInt(1_000_000)))
                .build();

        List<OrderItem> items = new ArrayList<>();
        long totalMinor = 0L;
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

            long priceMinor = item.getPriceMinor() != null
                    ? item.getPriceMinor() : Money.toMinor(item.getPrice());
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(item)
                    .quantity(itemDTO.getQuantity())
                    .priceEach(item.getPrice())
                    .priceEachMinor(priceMinor)
                    .currency(item.getCurrency() != null ? item.getCurrency() : Money.DEFAULT_CURRENCY)
                    .build();
            items.add(orderItem);
            totalMinor = Math.addExact(totalMinor,
                    Math.multiplyExact(priceMinor, itemDTO.getQuantity().longValue()));
        }
        order.setItems(items);
        order.setTotalAmount(Money.toMajor(totalMinor));
        order.setTotalAmountMinor(totalMinor);
        order.setCurrency(Money.DEFAULT_CURRENCY);

        orderRepository.save(order);
        recordEvent(order, null, OrderStatus.PLACED, user.getEmail(), "Order placed");
        realtimeNotifier.publish("ORDER_PLACED", order);
        return toResponseDTO(order, true);
    }

    @Override
    public OrderResponseDTO getOrderById(Long orderId, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User caller = resolveCaller(callerEmail);
        assertCanView(order, caller);
        return toResponseDTO(order, order.getUser().getUserId().equals(caller.getUserId()));
    }

    @Override
    public List<OrderResponseDTO> getOrdersByUser(Long userId) {
        return orderRepository.findByUserUserId(userId)
                .stream().map(order -> toResponseDTO(order, true)).collect(Collectors.toList());
    }

    @Override
    public List<OrderResponseDTO> getOrdersByMerchant(Long merchantId) {
        return orderRepository.findByMerchantMerchantId(merchantId)
                .stream().map(order -> toResponseDTO(order, false)).collect(Collectors.toList());
    }

    @Override
    public List<OrderResponseDTO> getOrdersForOwner(String ownerEmail) {
        User owner = resolveCaller(ownerEmail);
        return orderRepository.findByMerchant_User_UserId(owner.getUserId())
                .stream().map(order -> toResponseDTO(order, false)).collect(Collectors.toList());
    }

    @Override
    public Page<OrderResponseDTO> getOrdersForOwner(String ownerEmail, Pageable pageable) {
        User owner = resolveCaller(ownerEmail);
        return orderRepository.findByMerchant_User_UserId(owner.getUserId(), pageable)
                .map(order -> toResponseDTO(order, false));
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
        if (target == OrderStatus.ACCEPTED || target == OrderStatus.PREPARING) {
            assertPaymentCompleted(order);
        }

        order.setStatus(target);
        orderRepository.save(order);
        recordEvent(order, current, target, caller.getEmail(), "Status updated");
        if (target == OrderStatus.CANCELLED) {
            paymentService.refundCompletedPaymentForOrder(orderId);
        }
        realtimeNotifier.publish("ORDER_STATUS_CHANGED", order);
        return toResponseDTO(order, false);
    }

    @Transactional
    @Override
    public OrderResponseDTO acceptAndStartPreparation(Long orderId, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User caller = resolveCaller(callerEmail);
        assertCanManage(order, caller);
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new BadRequestException("Only newly placed orders can be accepted");
        }
        assertPaymentCompleted(order);

        // Retain both milestones for a trustworthy audit trail, but expose the useful state to
        // both live clients immediately: the shop is preparing and the customer's route is live.
        order.setStatus(OrderStatus.ACCEPTED);
        recordEvent(order, OrderStatus.PLACED, OrderStatus.ACCEPTED, caller.getEmail(),
                "Accepted by merchant; preparation started immediately");
        realtimeNotifier.publish("ORDER_STATUS_CHANGED", order);

        order.setStatus(OrderStatus.PREPARING);
        order.setPrepStartAt(LocalDateTime.now());
        orderRepository.save(order);
        recordEvent(order, OrderStatus.ACCEPTED, OrderStatus.PREPARING, caller.getEmail(),
                "Preparation started on acceptance");
        realtimeNotifier.publish("ORDER_STATUS_CHANGED", order);
        return toResponseDTO(order, false);
    }

    @Transactional
    @Override
    public OrderResponseDTO cancelOrder(Long orderId, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User caller = resolveCaller(callerEmail);
        if (!order.getUser().getUserId().equals(caller.getUserId())) {
            throw new ForbiddenException("You are not allowed to cancel this order");
        }
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new BadRequestException("Orders can only be cancelled before preparation begins");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        recordEvent(order, OrderStatus.PLACED, OrderStatus.CANCELLED, caller.getEmail(),
                "Cancelled by customer before preparation");
        paymentService.refundCompletedPaymentForOrder(orderId);
        realtimeNotifier.publish("ORDER_STATUS_CHANGED", order);
        return toResponseDTO(order, true);
    }

    @Transactional
    @Override
    public OrderResponseDTO confirmPickup(Long orderId, String pickupCode, String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User caller = resolveCaller(callerEmail);
        assertCanManage(order, caller);
        if (order.getStatus() != OrderStatus.READY) {
            throw new BadRequestException("Only ready orders can be handed over");
        }
        if (order.getPickupCode() == null || !order.getPickupCode().equals(pickupCode.trim())) {
            throw new BadRequestException("Pickup code does not match this order");
        }

        order.setStatus(OrderStatus.PICKED);
        orderRepository.save(order);
        recordEvent(order, OrderStatus.READY, OrderStatus.PICKED, caller.getEmail(),
                "Pickup code verified at hand-off");
        realtimeNotifier.publish("ORDER_STATUS_CHANGED", order);
        return toResponseDTO(order, false);
    }

    @Transactional
    @Override
    public EtaQuoteResponse updateLiveLocation(Long orderId, double latitude, double longitude,
                                               String callerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User caller = resolveCaller(callerEmail);

        // Only the customer who placed the order may stream their location for it.
        if (!order.getUser().getUserId().equals(caller.getUserId())) {
            throw new ForbiddenException("You are not allowed to update this order");
        }
        if (order.getStatus().isTerminal() || order.getStatus() == OrderStatus.READY) {
            throw new BadRequestException("This order is no longer en route");
        }
        if (order.getStatus() == OrderStatus.PLACED) {
            throw new BadRequestException("Live route timing starts once the shop accepts the order");
        }

        // Record the position ping for history/analytics.
        locationRepository.save(Location.builder()
                .user(caller)
                .latitude(latitude)
                .longitude(longitude)
                .recordedTime(LocalDateTime.now())
                .build());

        // Recompute the live ETA from the new position and re-sync the order. The location
        // belongs in the append-only location history; do not overwrite the checkout origin
        // used to draw the customer's route or a refreshed screen would collapse the map.
        Merchant merchant = order.getMerchant();
        EtaCalculation eta = etaService.estimate(new GeoPoint(latitude, longitude), merchant);
        order.setPickupTime(eta.readyAt());
        // A merchant who has already accepted through the normal flow is actively preparing;
        // retain that factual start time rather than replacing it with a future recommendation.
        if (order.getStatus() != OrderStatus.PREPARING) {
            order.setPrepStartAt(eta.prepStartAt());
        } else if (order.getPrepStartAt() == null) {
            order.setPrepStartAt(LocalDateTime.now());
        }
        int readyInMins = Math.max(0,
                (int) Duration.between(LocalDateTime.now(), eta.readyAt()).toMinutes());
        order.setEtaSegment(String.format(
                "live: travel %d min (+%d traffic), prep %d min (+%d buffer); ready ~%d min",
                eta.travelMins(), eta.trafficBufferMins(), eta.prepTimeMins(), eta.bufferMins(),
                readyInMins));
        orderRepository.save(order);
        realtimeNotifier.publish("ORDER_ETA_CHANGED", order);

        return com.ontheway.controller.EtaController.toQuote(merchant.getMerchantId(), eta);
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

    private void assertPaymentCompleted(Order order) {
        PaymentStatus paymentStatus = paymentRepository.findByOrderOrderId(order.getOrderId())
                .map(Payment::getPaymentStatus)
                .orElse(null);
        if (paymentStatus != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Payment must be completed before the shop can accept or begin preparation");
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

    private OrderResponseDTO toResponseDTO(Order order, boolean includeCustomerPosition) {
        List<OrderItemResponseDTO> items = order.getItems().stream().map(oi ->
                OrderItemResponseDTO.builder()
                        .orderItemId(oi.getOrderItemId())
                        .menuItemId(oi.getMenuItem().getMenuItemId())
                        .itemName(oi.getMenuItem().getName())
                        .quantity(oi.getQuantity())
                        .priceEach(oi.getPriceEach())
                        .priceEachMinor(oi.getPriceEachMinor())
                        .totalPrice(oi.getQuantity() * oi.getPriceEach())
                        .totalPriceMinor((oi.getPriceEachMinor() != null ? oi.getPriceEachMinor()
                            : Money.toMinor(oi.getPriceEach())) * oi.getQuantity())
                        .currency(oi.getCurrency() != null ? oi.getCurrency() : Money.DEFAULT_CURRENCY)
                        .build()
        ).collect(Collectors.toList());

        return OrderResponseDTO.builder()
                .orderId(order.getOrderId())
                .userId(order.getUser().getUserId())
                .merchantId(order.getMerchant().getMerchantId())
                .merchantName(order.getMerchant().getStoreName())
                .merchantLatitude(order.getMerchant().getLatitude())
                .merchantLongitude(order.getMerchant().getLongitude())
                .customerLatitude(includeCustomerPosition ? order.getCustomerLatitude() : null)
                .customerLongitude(includeCustomerPosition ? order.getCustomerLongitude() : null)
                .orderTime(order.getOrderTime())
                .pickupTime(order.getPickupTime())
                .prepStartAt(order.getPrepStartAt())
                .etaSegment(order.getEtaSegment())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .totalAmountMinor(order.getTotalAmountMinor())
                .currency(order.getCurrency() != null ? order.getCurrency() : Money.DEFAULT_CURRENCY)
                .pickupCode(order.getPickupCode())
                .items(items)
                .payment(order.getPayment() != null ? PaymentResponseDTO.builder()
                        .paymentId(order.getPayment().getPaymentId())
                        .orderId(order.getPayment().getOrder().getOrderId())
                        .paymentStatus(order.getPayment().getPaymentStatus())
                        .paymentMethod(order.getPayment().getPaymentMethod())
                        .amount(order.getPayment().getAmount())
                        .amountMinor(order.getPayment().getAmountMinor())
                        .currency(order.getPayment().getCurrency() != null ? order.getPayment().getCurrency() : Money.DEFAULT_CURRENCY)
                        .gateway(order.getPayment().getGateway())
                        .gatewayReference(order.getPayment().getGatewayReference())
                        .paymentTime(order.getPayment().getPaymentTime())
                        .attemptCount(order.getPayment().getAttemptCount())
                        .failureReason(order.getPayment().getFailureReason())
                        .build() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
