package com.ontheway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.*;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ConflictException;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.*;
import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.payment.ChargeResult;
import com.ontheway.payment.PaymentGateway;
import com.ontheway.repository.*;
import com.ontheway.service.PaymentService;
import com.ontheway.util.Money;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    @Transactional
    @Override
    public PaymentResponseDTO createPayment(PaymentCreateDTO dto, String callerEmail) {
        // Serialize payment creation per order. The database unique constraint remains the
        // final guard, while this lock prevents two requests from reaching the gateway.
        Order order = orderRepository.findByIdForUpdate(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        assertOwnsOrder(order, callerEmail);

        // Idempotency: an order has at most one payment.
        if (paymentRepository.findByOrderOrderId(order.getOrderId()).isPresent()) {
            throw new ConflictException("This order has already been paid");
        }

        // Charge through the configured gateway; the gateway decides the outcome,
        // never the client.
        String idempotencyKey = "order-" + order.getOrderId();
        ChargeResult result = paymentGateway.charge(
                order.getOrderId(), order.getTotalAmount(), dto.getPaymentMethod(), idempotencyKey);

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(dto.getPaymentMethod())
                .amount(order.getTotalAmount())
                .amountMinor(order.getTotalAmountMinor() != null
                    ? order.getTotalAmountMinor() : Money.toMinor(order.getTotalAmount()))
                .currency(order.getCurrency() != null ? order.getCurrency() : Money.DEFAULT_CURRENCY)
                .paymentStatus(result.status())
                .gateway(result.gateway())
                .gatewayReference(result.reference())
                .paymentTime(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        return toResponseDTO(payment);
    }

    @Override
    public PaymentResponseDTO getPaymentByOrderId(Long orderId, String callerEmail) {
        Payment payment = paymentRepository.findByOrderOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        assertCanViewOrder(payment.getOrder(), callerEmail);
        return toResponseDTO(payment);
    }

    @Transactional
    @Override
    public void updatePaymentStatus(Long paymentId, String status) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        PaymentStatus next = parseStatus(status);
        if (applyStatus(payment, next)) {
            paymentRepository.save(payment);
        }
    }

    @Transactional
    @Override
    public PaymentResponseDTO refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only completed payments can be refunded");
        }
        if (!paymentGateway.name().equalsIgnoreCase(payment.getGateway())) {
            throw new ConflictException("The configured payment gateway cannot refund this payment");
        }
        boolean refunded;
        try {
            refunded = paymentGateway.refund(payment.getOrder().getOrderId(),
                    payment.getGatewayReference(), payment.getAmount(), "refund-" + paymentId);
        } catch (UnsupportedOperationException ex) {
            throw new BadRequestException("Refunds are not supported by this payment gateway");
        }
        if (!refunded) {
            throw new BadRequestException("Payment gateway rejected the refund");
        }
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        return toResponseDTO(payment);
    }

    @Transactional
    @Override
    public void handleWebhook(String gateway, String payload, String signature) {
        String normalizedGateway = gateway == null ? "" : gateway.trim().toLowerCase(Locale.ROOT);
        if (!paymentGateway.name().equals(normalizedGateway)) {
            throw new BadRequestException("Webhook gateway does not match the configured provider");
        }
        if (!paymentGateway.verifyWebhook(payload, signature)) {
            throw new ForbiddenException("Invalid payment webhook signature");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid payment webhook payload");
        }
        if (root == null || !root.isObject()) {
            throw new BadRequestException("Invalid payment webhook payload");
        }

        String reference = referenceFromWebhook(normalizedGateway, root);
        if (reference == null || reference.isBlank()) {
            return;
        }
        Payment payment = paymentRepository.findByGatewayAndGatewayReference(normalizedGateway, reference)
                .orElse(null);
        if (payment == null) {
            return;
        }
        PaymentStatus next = statusFromWebhook(root);
        if (next != null && canApplyStatus(payment.getPaymentStatus(), next)) {
            payment.setPaymentStatus(next);
            if ("razorpay".equals(normalizedGateway) && next == PaymentStatus.COMPLETED) {
                String paymentReference = razorpayPaymentReference(root);
                if (paymentReference != null && !paymentReference.isBlank()) {
                    payment.setGatewayReference(paymentReference);
                }
            }
            paymentRepository.save(payment);
        }
    }

    // ----- helpers -------------------------------------------------------

    /** Only the order's owner may create its payment. */
    private void assertOwnsOrder(Order order, String callerEmail) {
        User caller = resolveCaller(callerEmail);
        if (!order.getUser().getUserId().equals(caller.getUserId())) {
            throw new ForbiddenException("You are not allowed to pay for this order");
        }
    }

    /** The order's owner, the serving merchant, or an admin may view the payment. */
    private void assertCanViewOrder(Order order, String callerEmail) {
        User caller = resolveCaller(callerEmail);
        boolean isOwner = order.getUser().getUserId().equals(caller.getUserId());
        boolean isServingMerchant = caller.getRole() == UserRole.MERCHANT
                && order.getMerchant().getUser().getUserId().equals(caller.getUserId());
        boolean isAdmin = caller.getRole() == UserRole.ADMIN;
        if (!(isOwner || isServingMerchant || isAdmin)) {
            throw new ForbiddenException("You are not allowed to access this payment");
        }
    }

    private User resolveCaller(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private PaymentResponseDTO toResponseDTO(Payment payment) {
        return PaymentResponseDTO.builder()
                .paymentId(payment.getPaymentId())
                .orderId(payment.getOrder().getOrderId())
                .paymentStatus(payment.getPaymentStatus())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .amountMinor(payment.getAmountMinor())
                .currency(payment.getCurrency() != null ? payment.getCurrency() : Money.DEFAULT_CURRENCY)
                .gateway(payment.getGateway())
                .gatewayReference(payment.getGatewayReference())
                .paymentTime(payment.getPaymentTime())
                .build();
    }

    private String referenceFromWebhook(String gateway, JsonNode root) {
        if (root.hasNonNull("gatewayReference")) {
            return root.get("gatewayReference").asText();
        }
        JsonNode object = root.path("data").path("object");
        if ("stripe".equalsIgnoreCase(gateway)) {
            String type = webhookType(root);
            if (type.contains("refund")) {
                return object.path("payment_intent").asText(null);
            }
            return object.path("id").asText(null);
        }
        if ("razorpay".equalsIgnoreCase(gateway)) {
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String orderId = payment.path("order_id").asText(null);
            if (orderId != null && !orderId.isBlank()) {
                return orderId;
            }
            JsonNode refund = root.path("payload").path("refund").path("entity");
            String paymentId = refund.path("payment_id").asText(null);
            return paymentId != null ? paymentId : object.path("id").asText(null);
        }
        return object.path("id").asText(null);
    }

    private PaymentStatus statusFromWebhook(JsonNode root) {
        if (root.hasNonNull("status")) {
            return parseStatus(root.get("status").asText());
        }
        String type = webhookType(root);
        if (type.contains("refund")) {
            return PaymentStatus.REFUNDED;
        }
        if (type.contains("succeeded") || type.contains("captured") || type.contains("authorized")) {
            return PaymentStatus.COMPLETED;
        }
        if (type.contains("failed")) {
            return PaymentStatus.FAILED;
        }
        return null;
    }

    private String webhookType(JsonNode root) {
        return root.path("type").asText(root.path("event").asText(""))
                .trim().toLowerCase(Locale.ROOT);
    }

    private String razorpayPaymentReference(JsonNode root) {
        return root.path("payload").path("payment").path("entity").path("id").asText(null);
    }

    private PaymentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Payment status is required");
        }
        try {
            return PaymentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown payment status: " + value);
        }
    }

    /**
     * Applies monotonic payment-state changes. Completed/refunded events must never be
     * overwritten by a delayed pending or failed webhook.
     */
    private boolean applyStatus(Payment payment, PaymentStatus next) {
        PaymentStatus current = payment.getPaymentStatus();
        if (current == next) {
            return false;
        }
        if (!canApplyStatus(current, next)) {
            throw new ConflictException("Cannot change payment status from " + current + " to " + next);
        }
        payment.setPaymentStatus(next);
        return true;
    }

    private boolean canApplyStatus(PaymentStatus current, PaymentStatus next) {
        return switch (next) {
            case PENDING -> false;
            case FAILED -> current == PaymentStatus.PENDING;
            case COMPLETED -> current == PaymentStatus.PENDING || current == PaymentStatus.FAILED;
            case REFUNDED -> current == PaymentStatus.PENDING || current == PaymentStatus.COMPLETED;
        };
    }
}
