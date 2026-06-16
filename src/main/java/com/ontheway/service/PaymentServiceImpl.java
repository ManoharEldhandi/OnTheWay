package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.*;
import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.*;
import com.ontheway.service.PaymentService;

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

    @Transactional
    @Override
    public PaymentResponseDTO createPayment(PaymentCreateDTO dto, String callerEmail) {
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        assertOwnsOrder(order, callerEmail);
        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(dto.getPaymentMethod())
                .amount(order.getTotalAmount())
                .paymentStatus(PaymentStatus.PENDING)
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
                .paymentTime(payment.getPaymentTime())
                .build();
    }
}
