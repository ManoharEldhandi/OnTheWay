package com.ontheway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.*;
import com.ontheway.model.User;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end payment flow through the mock gateway: pay an order, see COMPLETED,
 * and confirm idempotency (second charge → 409).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.ontheway.repository.MerchantRepository merchantRepository;
        @Autowired private com.ontheway.repository.UserRepository userRepository;
        @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        UserCreateDTO reg = UserCreateDTO.builder()
                .email(email).password("password123").name("Test").role(role).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        LoginRequest login = LoginRequest.builder().email(email).password("password123").build();
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    private long json(String body, String field) throws Exception {
        return objectMapper.readTree(body).get(field).asLong();
    }

        private String adminToken(String email) throws Exception {
                userRepository.save(User.builder()
                                .email(email).password(passwordEncoder.encode("password123"))
                                .name("Admin").role(UserRole.ADMIN).build());
                LoginRequest login = LoginRequest.builder().email(email).password("password123").build();
                String body = mockMvc.perform(post("/api/auth/login")
                                                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login)))
                                .andReturn().getResponse().getContentAsString();
                return "Bearer " + objectMapper.readTree(body).get("token").asText();
        }

    /** Applies for a shop as the given merchant (Bearer token) and approves it; returns shop id. */
    private long applyApprove(String bearerToken, MerchantCreateDTO dto) throws Exception {
        return com.ontheway.support.TestFixtures.applyAndApproveShop(
                mockMvc, objectMapper, merchantRepository, bearerToken, dto);
    }

    @Test
    void payOrder_throughMockGateway_completesAndIsIdempotent() throws Exception {
        String merchantToken = registerAndLogin("pay-merch@x.com", UserRole.MERCHANT);
        long merchantId = applyApprove(merchantToken,
                MerchantCreateDTO.builder().storeName("Pay Store").storeType(StoreType.RESTAURANT)
                        .address("addr").etaBufferMins(5).build());
        long menuItemId = json(mockMvc.perform(post("/api/menu-items/" + merchantId).header("Authorization", merchantToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        MenuItemCreateDTO.builder().name("Combo").price(50.0).availability(true).build())))
                .andReturn().getResponse().getContentAsString(), "menuItemId");

        String custToken = registerAndLogin("pay-cust@x.com", UserRole.USER);
        long orderId = json(mockMvc.perform(post("/api/orders").header("Authorization", custToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        OrderCreateDTO.builder().merchantId(merchantId)
                                .pickupTime(LocalDateTime.now().plusMinutes(30)).paymentMethod("CARD")
                                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(2).build()))
                                .build())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "orderId");

        // Pay -> COMPLETED via mock gateway
        mockMvc.perform(post("/api/payments").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                PaymentCreateDTO.builder().orderId(orderId).paymentMethod("CARD").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.gateway").value("mock"))
                .andExpect(jsonPath("$.amount").value(100.0))
                .andExpect(jsonPath("$.amountMinor").value(10000))
                .andExpect(jsonPath("$.currency").value("INR"));

        // Idempotent: second payment attempt -> 409
        mockMvc.perform(post("/api/payments").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                PaymentCreateDTO.builder().orderId(orderId).paymentMethod("CARD").build())))
                .andExpect(status().isConflict());
    }

    @Test
    void webhookAndRefund_areSignatureCheckedAndAuditable() throws Exception {
        String merchantToken = registerAndLogin("refund-merch@x.com", UserRole.MERCHANT);
        long merchantId = applyApprove(merchantToken,
                MerchantCreateDTO.builder().storeName("Refund Store").storeType(StoreType.RESTAURANT)
                        .address("addr").etaBufferMins(5).build());
        long menuItemId = json(mockMvc.perform(post("/api/menu-items/" + merchantId).header("Authorization", merchantToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        MenuItemCreateDTO.builder().name("Meal").price(80.0).availability(true).build())))
                .andReturn().getResponse().getContentAsString(), "menuItemId");

        String custToken = registerAndLogin("refund-cust@x.com", UserRole.USER);
        long orderId = json(mockMvc.perform(post("/api/orders").header("Authorization", custToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        OrderCreateDTO.builder().merchantId(merchantId)
                                .pickupTime(LocalDateTime.now().plusMinutes(30)).paymentMethod("CARD")
                                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(1).build()))
                                .build())))
                .andReturn().getResponse().getContentAsString(), "orderId");

        String paymentBody = mockMvc.perform(post("/api/payments").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                PaymentCreateDTO.builder().orderId(orderId).paymentMethod("CARD").build())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(paymentBody).get("paymentId").asLong();
        String reference = objectMapper.readTree(paymentBody).get("gatewayReference").asText();

        mockMvc.perform(post("/api/payments/webhook/mock")
                        .header("X-Mock-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatewayReference\":\"" + reference + "\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payments/webhook/mock")
                        .header("X-Mock-Signature", "mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatewayReference\":\"" + reference + "\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNoContent());

        String admin = adminToken("refund-admin@x.com");
        mockMvc.perform(post("/api/payments/webhook/stripe")
                        .header("X-Mock-Signature", "mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gatewayReference\":\"" + reference + "\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/payments/" + paymentId + "/status")
                        .header("Authorization", admin)
                        .param("status", "successful"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payments/" + paymentId + "/refund").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        mockMvc.perform(put("/api/payments/" + paymentId + "/status")
                        .header("Authorization", admin)
                        .param("status", "COMPLETED"))
                .andExpect(status().isConflict());
    }

    @Test
    void payOrder_byNonOwner_isForbidden() throws Exception {
        String merchantToken = registerAndLogin("pay2-merch@x.com", UserRole.MERCHANT);
        long merchantId = applyApprove(merchantToken,
                MerchantCreateDTO.builder().storeName("S2").storeType(StoreType.CAFE)
                        .address("a").etaBufferMins(5).build());
        long menuItemId = json(mockMvc.perform(post("/api/menu-items/" + merchantId).header("Authorization", merchantToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        MenuItemCreateDTO.builder().name("Tea").price(20.0).availability(true).build())))
                .andReturn().getResponse().getContentAsString(), "menuItemId");

        String ownerToken = registerAndLogin("pay2-owner@x.com", UserRole.USER);
        long orderId = json(mockMvc.perform(post("/api/orders").header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        OrderCreateDTO.builder().merchantId(merchantId)
                                .pickupTime(LocalDateTime.now().plusMinutes(30)).paymentMethod("CARD")
                                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(1).build()))
                                .build())))
                .andReturn().getResponse().getContentAsString(), "orderId");

        String strangerToken = registerAndLogin("pay2-stranger@x.com", UserRole.USER);
        mockMvc.perform(post("/api/payments").header("Authorization", strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                PaymentCreateDTO.builder().orderId(orderId).paymentMethod("CARD").build())))
                .andExpect(status().isForbidden());
    }
}
