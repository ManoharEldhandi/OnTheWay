package com.ontheway.service.impl;

import com.ontheway.dto.PaymentCreateDTO;
import com.ontheway.dto.PaymentResponseDTO;
import com.ontheway.exception.ConflictException;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.model.*;
import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.payment.ChargeResult;
import com.ontheway.payment.PaymentGateway;
import com.ontheway.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentGateway paymentGateway;
    @InjectMocks private PaymentServiceImpl paymentService;

    private User customer;
    private Order order;

    @BeforeEach
    void setup() {
        customer = User.builder().userId(1L).email("cust@x.com").role(UserRole.USER).build();
        order = Order.builder().orderId(50L).user(customer).totalAmount(100.0).build();
    }

    @Test
    void createPayment_chargesGateway_andMarksCompleted() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("cust@x.com")).thenReturn(Optional.of(customer));
        when(paymentRepository.findByOrderOrderId(50L)).thenReturn(Optional.empty());
        when(paymentGateway.charge(eq(50L), eq(100.0), eq("CARD"), anyString()))
                .thenReturn(new ChargeResult(true, "mock_ref123", "mock"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDTO res = paymentService.createPayment(
                PaymentCreateDTO.builder().orderId(50L).paymentMethod("CARD").build(), "cust@x.com");

        assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(res.getGateway()).isEqualTo("mock");
        assertThat(res.getGatewayReference()).isEqualTo("mock_ref123");
    }

    @Test
    void createPayment_whenGatewayDeclines_marksFailed() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("cust@x.com")).thenReturn(Optional.of(customer));
        when(paymentRepository.findByOrderOrderId(50L)).thenReturn(Optional.empty());
        when(paymentGateway.charge(anyLong(), anyDouble(), anyString(), anyString()))
                .thenReturn(new ChargeResult(false, "mock_decline", "mock"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDTO res = paymentService.createPayment(
                PaymentCreateDTO.builder().orderId(50L).paymentMethod("CARD").build(), "cust@x.com");

        assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void createPayment_isIdempotent_secondAttemptConflicts() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("cust@x.com")).thenReturn(Optional.of(customer));
        when(paymentRepository.findByOrderOrderId(50L)).thenReturn(Optional.of(new Payment()));

        assertThatThrownBy(() -> paymentService.createPayment(
                PaymentCreateDTO.builder().orderId(50L).paymentMethod("CARD").build(), "cust@x.com"))
                .isInstanceOf(ConflictException.class);

        verify(paymentGateway, never()).charge(anyLong(), anyDouble(), anyString(), anyString());
    }

    @Test
    void createPayment_byNonOwner_isForbidden() {
        User stranger = User.builder().userId(9L).email("stranger@x.com").role(UserRole.USER).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("stranger@x.com")).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> paymentService.createPayment(
                PaymentCreateDTO.builder().orderId(50L).paymentMethod("CARD").build(), "stranger@x.com"))
                .isInstanceOf(ForbiddenException.class);
    }
}
