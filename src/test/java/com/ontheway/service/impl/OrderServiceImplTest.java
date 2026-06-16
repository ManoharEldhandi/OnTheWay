package com.ontheway.service.impl;

import com.ontheway.dto.OrderCreateDTO;
import com.ontheway.dto.OrderItemCreateDTO;
import com.ontheway.dto.OrderResponseDTO;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.model.*;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderEventRepository orderEventRepository;
    @Mock private com.ontheway.fulfillment.EtaService etaService;
    @InjectMocks private OrderServiceImpl orderService;

    private User customer;
    private User merchantUser;
    private Merchant merchant;
    private MenuItem item;

    @BeforeEach
    void setup() {
        customer = User.builder().userId(1L).email("cust@x.com").role(UserRole.USER).build();
        merchantUser = User.builder().userId(2L).email("merch@x.com").role(UserRole.MERCHANT).build();
        merchant = Merchant.builder().merchantId(10L).user(merchantUser).build();
        item = MenuItem.builder().menuItemId(100L).merchant(merchant)
                .name("Burger").price(5.0).availability(true).build();
    }

    private OrderCreateDTO orderDto(Long menuItemId, int qty) {
        return OrderCreateDTO.builder()
                .merchantId(10L)
                .pickupTime(LocalDateTime.now().plusMinutes(30))
                .paymentMethod("CARD")
                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(qty).build()))
                .build();
    }

    @Test
    void placeOrder_rejectsItemFromAnotherMerchant() {
        Merchant other = Merchant.builder().merchantId(99L).user(merchantUser).build();
        MenuItem foreign = MenuItem.builder().menuItemId(100L).merchant(other)
                .price(5.0).availability(true).name("X").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(merchant));
        when(menuItemRepository.findById(100L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> orderService.placeOrder(1L, orderDto(100L, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not belong");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_rejectsUnavailableItem() {
        item.setAvailability(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(merchant));
        when(menuItemRepository.findById(100L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> orderService.placeOrder(1L, orderDto(100L, 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void placeOrder_computesTotalAndRecordsPlacedEvent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(merchantRepository.findById(10L)).thenReturn(Optional.of(merchant));
        when(menuItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponseDTO result = orderService.placeOrder(1L, orderDto(100L, 3));

        assertThat(result.getTotalAmount()).isEqualTo(15.0);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED);
        verify(orderEventRepository).save(argThat(e ->
                e.getFromStatus() == null && e.getToStatus() == OrderStatus.PLACED));
    }

    @Test
    void getOrderById_deniesNonOwner() {
        User stranger = User.builder().userId(7L).email("stranger@x.com").role(UserRole.USER).build();
        Order order = Order.builder().orderId(50L).user(customer).merchant(merchant)
                .status(OrderStatus.PLACED).items(List.of()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("stranger@x.com")).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> orderService.getOrderById(50L, "stranger@x.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getOrderById_allowsOwner() {
        Order order = Order.builder().orderId(50L).user(customer).merchant(merchant)
                .status(OrderStatus.PLACED).items(List.of()).totalAmount(15.0)
                .orderTime(LocalDateTime.now()).pickupTime(LocalDateTime.now()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("cust@x.com")).thenReturn(Optional.of(customer));

        OrderResponseDTO result = orderService.getOrderById(50L, "cust@x.com");
        assertThat(result.getOrderId()).isEqualTo(50L);
    }

    @Test
    void updateOrderStatus_deniesMerchantWhoDoesNotOwnOrder() {
        User otherMerchUser = User.builder().userId(8L).email("other@x.com").role(UserRole.MERCHANT).build();
        Order order = Order.builder().orderId(50L).user(customer).merchant(merchant)
                .status(OrderStatus.PLACED).items(List.of()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("other@x.com")).thenReturn(Optional.of(otherMerchUser));

        assertThatThrownBy(() -> orderService.updateOrderStatus(50L, "PREPARING", "other@x.com"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateOrderStatus_rejectsIllegalTransition() {
        Order order = Order.builder().orderId(50L).user(customer).merchant(merchant)
                .status(OrderStatus.PLACED).items(List.of()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("merch@x.com")).thenReturn(Optional.of(merchantUser));

        assertThatThrownBy(() -> orderService.updateOrderStatus(50L, "PICKED", "merch@x.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Illegal status transition");
    }

    @Test
    void updateOrderStatus_appliesValidTransitionAndAudits() {
        Order order = Order.builder().orderId(50L).user(customer).merchant(merchant)
                .status(OrderStatus.PLACED).items(List.of()).totalAmount(0.0)
                .orderTime(LocalDateTime.now()).pickupTime(LocalDateTime.now()).build();
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmailIgnoreCase("merch@x.com")).thenReturn(Optional.of(merchantUser));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponseDTO result = orderService.updateOrderStatus(50L, "PREPARING", "merch@x.com");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderEventRepository).save(argThat(e ->
                e.getFromStatus() == OrderStatus.PLACED && e.getToStatus() == OrderStatus.PREPARING));
    }
}
