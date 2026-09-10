package com.ontheway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.*;
import com.ontheway.fulfillment.OrderProgressionScheduler;
import com.ontheway.model.Order;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.OrderEventRepository;
import com.ontheway.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end order lifecycle through the HTTP layer:
 * merchant onboarding -> menu -> customer order -> ownership -> state machine.
 * Acts as the Phase 0 regression suite for ordering + authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.ontheway.repository.MerchantRepository merchantRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderEventRepository orderEventRepository;
    @Autowired private OrderProgressionScheduler orderProgressionScheduler;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        UserCreateDTO reg = UserCreateDTO.builder()
                .email(email).password("password123").name("Test").role(role).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        LoginRequest login = LoginRequest.builder().email(email).password("password123").build();
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private long idFrom(String body, String field) throws Exception {
        return objectMapper.readTree(body).get(field).asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    /** Applies for a shop as the given merchant and approves it, returning the shop id. */
    private long applyApprove(String token, MerchantCreateDTO dto) throws Exception {
        return com.ontheway.support.TestFixtures.applyAndApproveShop(
                mockMvc, objectMapper, merchantRepository, bearer(token), dto);
    }

    @Test
    void fullOrderLifecycle_withOwnershipAndStateMachine() throws Exception {
        // --- Merchant onboarding ---
        String merchantToken = registerAndLogin("flow-merchant@x.com", UserRole.MERCHANT);
        MerchantCreateDTO merchantDto = MerchantCreateDTO.builder()
                .storeName("Flow Cafe").storeType(StoreType.CAFE)
                .address("1 Test St").latitude(12.9716).longitude(77.5946)
                .prepTimeMins(8).etaBufferMins(10).build();
        long merchantId = applyApprove(merchantToken, merchantDto);

        // --- Menu item ---
        MenuItemCreateDTO itemDto = MenuItemCreateDTO.builder()
                .name("Latte").description("Hot").price(4.0).availability(true).build();
        String itemBody = mockMvc.perform(post("/api/menu-items/" + merchantId)
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(itemDto)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long menuItemId = idFrom(itemBody, "menuItemId");

        // --- Customer places an order ---
        String customerToken = registerAndLogin("flow-customer@x.com", UserRole.USER);
        OrderCreateDTO orderDto = OrderCreateDTO.builder()
                .merchantId(merchantId)
                .pickupTime(LocalDateTime.now().plusMinutes(30))
                .latitude(12.9868).longitude(77.5732)
                .paymentMethod("CARD")
                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(2).build()))
                .build();
        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(orderDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.totalAmount").value(8.0))
                .andReturn().getResponse().getContentAsString();
        long orderId = idFrom(orderBody, "orderId");
        String pickupCode = objectMapper.readTree(orderBody).get("pickupCode").asText();

        // Historical data is immutable: referenced catalog/account records cannot be deleted.
        mockMvc.perform(delete("/api/menu-items/" + menuItemId)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/merchant/shops/" + merchantId)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isConflict());
        String customerBody = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long customerId = idFrom(customerBody, "userId");
        mockMvc.perform(delete("/api/users/" + customerId)
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isConflict());

        // --- Owner can view the order ---
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerLatitude").value(12.9868))
                .andExpect(jsonPath("$.customerLongitude").value(77.5732));

        // Merchant visibility is deliberately ETA-only: even a serving merchant cannot receive
        // the stored route position from the generic order endpoint.
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerLatitude").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.customerLongitude").value(org.hamcrest.Matchers.nullValue()));

        // --- IDOR: a different customer cannot view it ---
        String strangerToken = registerAndLogin("flow-stranger@x.com", UserRole.USER);
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", bearer(strangerToken)))
                .andExpect(status().isForbidden());

        // --- An unpaid order is never allowed to be accepted ---
        mockMvc.perform(put("/api/orders/" + orderId + "/status").param("status", "ACCEPTED")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment must be completed before the shop can accept or begin preparation"));
        mockMvc.perform(post("/api/orders/" + orderId + "/messages")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Can you add a note?\"}"))
                .andExpect(status().isBadRequest());

        // --- Customer pays before a merchant can accept and begin preparation ---
        mockMvc.perform(post("/api/payments").header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PaymentCreateDTO.builder()
                                .orderId(orderId).paymentMethod("CARD").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"));

        // --- The normal merchant action accepts and begins preparation atomically ---
        mockMvc.perform(post("/api/orders/" + orderId + "/accept")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
        assertThat(orderRepository.findById(orderId).orElseThrow().getPrepStartAt())
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(1));
        assertThat(orderEventRepository.findByOrderOrderIdOrderByCreatedAtAsc(orderId))
                .anySatisfy(event -> {
                    assertThat(event.getFromStatus()).isEqualTo(OrderStatus.PLACED);
                    assertThat(event.getToStatus()).isEqualTo(OrderStatus.ACCEPTED);
                })
                .anySatisfy(event -> {
                    assertThat(event.getFromStatus()).isEqualTo(OrderStatus.ACCEPTED);
                    assertThat(event.getToStatus()).isEqualTo(OrderStatus.PREPARING);
                });

        // --- The private order thread opens only after acceptance and is shared both ways ---
        mockMvc.perform(post("/api/orders/" + orderId + "/messages")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Please keep the latte less sweet\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("USER"));
        mockMvc.perform(get("/api/orders/" + orderId + "/messages")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Please keep the latte less sweet"));
        mockMvc.perform(post("/api/orders/" + orderId + "/messages")
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Noted — we will do that.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("MERCHANT"));
        mockMvc.perform(get("/api/orders/" + orderId + "/messages")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].body").value("Noted — we will do that."));
        // --- Illegal transition PREPARING -> PICKED is rejected ---
        mockMvc.perform(put("/api/orders/" + orderId + "/status").param("status", "PICKED")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isBadRequest());

        // --- Ready orders are handed over only after the customer code is verified ---
        mockMvc.perform(put("/api/orders/" + orderId + "/status").param("status", "READY")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(post("/api/orders/" + orderId + "/pickup")
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pickupCode\":\"000000\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders/" + orderId + "/pickup")
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("pickupCode", pickupCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED"));

        // --- A customer cannot change order status (role-restricted) ---
        mockMvc.perform(put("/api/orders/" + orderId + "/status").param("status", "READY")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void placeOrder_withForeignMenuItem_isRejected() throws Exception {
        // Merchant A with an item
        String mAtoken = registerAndLogin("mA@x.com", UserRole.MERCHANT);
        long mA = applyApprove(mAtoken, MerchantCreateDTO.builder().storeName("A").storeType(StoreType.RESTAURANT)
                .address("A").etaBufferMins(5).build());
        long itemA = idFrom(mockMvc.perform(post("/api/menu-items/" + mA).header("Authorization", bearer(mAtoken))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        MenuItemCreateDTO.builder().name("Pizza").price(9.0).availability(true).build())))
                .andReturn().getResponse().getContentAsString(), "menuItemId");

        // Merchant B (target merchant for the order)
        String mBtoken = registerAndLogin("mB@x.com", UserRole.MERCHANT);
        long mB = applyApprove(mBtoken, MerchantCreateDTO.builder().storeName("B").storeType(StoreType.RESTAURANT)
                .address("B").etaBufferMins(5).build());

        // Customer tries to order merchant A's item from merchant B -> rejected
        String custToken = registerAndLogin("cross-cust@x.com", UserRole.USER);
        OrderCreateDTO bad = OrderCreateDTO.builder()
                .merchantId(mB).pickupTime(LocalDateTime.now().plusMinutes(20)).paymentMethod("CARD")
                .items(List.of(OrderItemCreateDTO.builder().menuItemId(itemA).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/orders").header("Authorization", bearer(custToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void schedulerAdvancesDueOrder_andPublishesWithLazyAssociationsLoaded() throws Exception {
        String merchantToken = registerAndLogin("scheduler-merchant@x.com", UserRole.MERCHANT);
        long merchantId = applyApprove(merchantToken, MerchantCreateDTO.builder()
                .storeName("Scheduler Cafe").storeType(StoreType.CAFE)
                .address("3 Test St").etaBufferMins(5).build());
        long menuItemId = idFrom(mockMvc.perform(post("/api/menu-items/" + merchantId)
                        .header("Authorization", bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MenuItemCreateDTO.builder()
                                .name("Espresso").price(3.0).availability(true).build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "menuItemId");

        String customerToken = registerAndLogin("scheduler-customer@x.com", UserRole.USER);
        String orderBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OrderCreateDTO.builder()
                                .merchantId(merchantId)
                                .pickupTime(LocalDateTime.now().plusMinutes(30))
                                .paymentMethod("CARD")
                                .items(List.of(OrderItemCreateDTO.builder()
                                        .menuItemId(menuItemId).quantity(1).build()))
                                .build())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = idFrom(orderBody, "orderId");

        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setPrepStartAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.saveAndFlush(order);

        mockMvc.perform(post("/api/payments").header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PaymentCreateDTO.builder()
                                .orderId(orderId).paymentMethod("CARD").build())))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/orders/" + orderId + "/status").param("status", "ACCEPTED")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk());

        orderProgressionScheduler.tick();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PREPARING);
        assertThat(orderEventRepository.findByOrderOrderIdOrderByCreatedAtAsc(orderId))
                .anySatisfy(event -> {
                    assertThat(event.getFromStatus()).isEqualTo(OrderStatus.ACCEPTED);
                    assertThat(event.getToStatus()).isEqualTo(OrderStatus.PREPARING);
                    assertThat(event.getChangedBy()).isEqualTo("system:scheduler");
                });
    }
}
