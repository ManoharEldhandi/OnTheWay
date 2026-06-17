package com.ontheway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.*;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for geo store discovery (radius + category filter + ordering).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscoveryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MerchantRepository merchantRepository;

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

    private void createStore(String emailPrefix, String name, StoreType type,
                             double lat, double lng) throws Exception {
        String token = registerAndLogin(emailPrefix + "@x.com", UserRole.MERCHANT);
        MerchantCreateDTO dto = MerchantCreateDTO.builder()
                .storeName(name).storeType(type).address("addr")
                .latitude(lat).longitude(lng).prepTimeMins(10).etaBufferMins(5).build();
        TestFixtures.applyAndApproveShop(mockMvc, objectMapper, merchantRepository, token, dto);
    }

    @Test
    void nearby_returnsStoresWithinRadius_nearestFirst_andFiltersByCategory() throws Exception {
        // Use a unique geographic cluster (New York) so stores created by other test
        // classes (around Bangalore) cannot leak into this shared in-memory dataset.
        createStore("disc-near", "Near Restaurant", StoreType.RESTAURANT, 40.7128, -74.0060);  // 0 km
        createStore("disc-mid", "Mid Pharmacy", StoreType.PHARMACY, 40.7250, -74.0060);        // ~1.4 km
        createStore("disc-far", "Far Restaurant", StoreType.RESTAURANT, 41.5000, -74.0060);    // ~88 km

        String custToken = registerAndLogin("disc-cust@x.com", UserRole.USER);

        // Within 5 km: the near + mid stores, nearest first; far excluded
        mockMvc.perform(get("/api/discovery/nearby")
                        .header("Authorization", custToken)
                        .param("lat", "40.7128").param("lng", "-74.0060").param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].storeName").value("Near Restaurant"))
                .andExpect(jsonPath("$[1].storeName").value("Mid Pharmacy"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0));

        // Category filter: only pharmacies
        mockMvc.perform(get("/api/discovery/nearby")
                        .header("Authorization", custToken)
                        .param("lat", "40.7128").param("lng", "-74.0060")
                        .param("radiusKm", "5").param("category", "PHARMACY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storeType").value("PHARMACY"));
    }

    @Test
    void nearby_withInvalidRadius_isBadRequest() throws Exception {
        String custToken = registerAndLogin("disc-bad@x.com", UserRole.USER);
        mockMvc.perform(get("/api/discovery/nearby")
                        .header("Authorization", custToken)
                        .param("lat", "12.97").param("lng", "77.59").param("radiusKm", "0"))
                .andExpect(status().isBadRequest());
    }
}
