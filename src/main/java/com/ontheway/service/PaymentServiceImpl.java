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

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    @Transactional
    @Override
    public PaymentResponseDTO createPayment(PaymentCreateDTO dto, String callerEmail) {
        Order order = orderRepository.findById(dto.getOrderId())
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
        payment.setPaymentStatus(PaymentStatus.valueOf(status));
        paymentRepository.save(payment);
    }

    @Transactional
    @Override
    public PaymentResponseDTO refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only completed payments can be refunded");
        }
        boolean refunded = paymentGateway.refund(payment.getOrder().getOrderId(),
                payment.getGatewayReference(), payment.getAmount(), "refund-" + paymentId);
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
        if (!paymentGateway.verifyWebhook(payload, signature)) {
            throw new ForbiddenException("Invalid payment webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String reference = referenceFromWebhook(gateway, root);
            if (reference == null || reference.isBlank()) {
                return;
            }
            Payment payment = paymentRepository.findByGatewayAndGatewayReference(gateway, reference)
                    .orElse(null);
            if (payment == null) {
                return;
            }
            PaymentStatus next = statusFromWebhook(root);
            if (next != null) {
                payment.setPaymentStatus(next);
                paymentRepository.save(payment);
            }
        } catch (Exception ex) {
            throw new BadRequestException("Invalid payment webhook payload");
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
            return object.path("id").asText(null);
        }
        if ("razorpay".equalsIgnoreCase(gateway)) {
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String paymentId = payment.path("id").asText(null);
            return paymentId != null ? paymentId : object.path("id").asText(null);
        }
        return object.path("id").asText(null);
    }

    private PaymentStatus statusFromWebhook(JsonNode root) {
        if (root.hasNonNull("status")) {
            return PaymentStatus.valueOf(root.get("status").asText().trim().toUpperCase());
        }
        String type = root.path("type").asText(root.path("event").asText(""));
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
}
