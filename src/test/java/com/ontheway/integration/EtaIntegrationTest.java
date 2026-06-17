package com.ontheway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.*;
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
 * End-to-end tests for the ETA engine: the quote endpoint and ETA-synchronized ordering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EtaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.ontheway.repository.MerchantRepository merchantRepository;

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

    private long createStoreWithGeo(String token, double lat, double lng, int prep) throws Exception {
        MerchantCreateDTO dto = MerchantCreateDTO.builder()
                .storeName("Geo Store").storeType(StoreType.RESTAURANT).address("addr")
                .latitude(lat).longitude(lng).prepTimeMins(prep).etaBufferMins(5).build();
        return com.ontheway.support.TestFixtures.applyAndApproveShop(
                mockMvc, objectMapper, merchantRepository, token, dto);
    }

    @Test
    void etaQuote_returnsTravelAndReadyTimes() throws Exception {
        String merchantToken = registerAndLogin("eta-merch@x.com", UserRole.MERCHANT);
        long merchantId = createStoreWithGeo(merchantToken, 12.9716, 77.5946, 10);

        String custToken = registerAndLogin("eta-cust@x.com", UserRole.USER);
        EtaQuoteRequest q = EtaQuoteRequest.builder()
                .merchantId(merchantId).latitude(13.0716).longitude(77.6946).build(); // ~15 km away
        mockMvc.perform(post("/api/eta/quote").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(q)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelMins").isNumber())
                .andExpect(jsonPath("$.distanceKm").isNumber())
                .andExpect(jsonPath("$.readyAt").isNotEmpty())
                .andExpect(jsonPath("$.prepStartAt").isNotEmpty());
    }

    @Test
    void etaQuote_forStoreWithoutLocation_isBadRequest() throws Exception {
        String merchantToken = registerAndLogin("noloc-merch@x.com", UserRole.MERCHANT);
        MerchantCreateDTO dto = MerchantCreateDTO.builder()
                .storeName("No Geo").storeType(StoreType.CAFE).address("addr").etaBufferMins(5).build();
        String body = mockMvc.perform(post("/api/merchant/shops").header("Authorization", merchantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long merchantId = objectMapper.readTree(body).get("merchantId").asLong();

        String custToken = registerAndLogin("noloc-cust@x.com", UserRole.USER);
        EtaQuoteRequest q = EtaQuoteRequest.builder()
                .merchantId(merchantId).latitude(13.0).longitude(77.6).build();
        mockMvc.perform(post("/api/eta/quote").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(q)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_withLiveLocation_isEtaSynchronized() throws Exception {
        String merchantToken = registerAndLogin("sync-merch@x.com", UserRole.MERCHANT);
        long merchantId = createStoreWithGeo(merchantToken, 12.9716, 77.5946, 10);
        MenuItemCreateDTO item = MenuItemCreateDTO.builder()
                .name("Dosa").price(3.0).availability(true).build();
        String itemBody = mockMvc.perform(post("/api/menu-items/" + merchantId).header("Authorization", merchantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long menuItemId = objectMapper.readTree(itemBody).get("menuItemId").asLong();

        String custToken = registerAndLogin("sync-cust@x.com", UserRole.USER);
        OrderCreateDTO order = OrderCreateDTO.builder()
                .merchantId(merchantId)
                .latitude(13.0716).longitude(77.6946) // location instead of pickupTime
                .paymentMethod("CARD")
                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/orders").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.pickupTime").isNotEmpty())
                .andExpect(jsonPath("$.etaSegment").isNotEmpty());
    }

    @Test
    void placeOrder_withoutPickupTimeOrLocation_isBadRequest() throws Exception {
        String merchantToken = registerAndLogin("nopt-merch@x.com", UserRole.MERCHANT);
        long merchantId = createStoreWithGeo(merchantToken, 12.9716, 77.5946, 10);
        MenuItemCreateDTO item = MenuItemCreateDTO.builder()
                .name("Idli").price(2.0).availability(true).build();
        String itemBody = mockMvc.perform(post("/api/menu-items/" + merchantId).header("Authorization", merchantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(item)))
                .andReturn().getResponse().getContentAsString();
        long menuItemId = objectMapper.readTree(itemBody).get("menuItemId").asLong();

        String custToken = registerAndLogin("nopt-cust@x.com", UserRole.USER);
        OrderCreateDTO order = OrderCreateDTO.builder()
                .merchantId(merchantId).paymentMethod("CARD")
                .items(List.of(OrderItemCreateDTO.builder().menuItemId(menuItemId).quantity(1).build()))
                .build(); // no pickupTime, no location
        mockMvc.perform(post("/api/orders").header("Authorization", custToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isBadRequest());
    }
}
