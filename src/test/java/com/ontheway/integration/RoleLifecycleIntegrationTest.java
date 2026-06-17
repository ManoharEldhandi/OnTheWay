package com.ontheway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.*;
import com.ontheway.model.User;
import com.ontheway.model.enums.StoreType;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for the role-separated marketplace: the shop lifecycle (apply → approve /
 * reject / suspend), multi-shop ownership, admin moderation and metrics, and the authorization
 * boundaries between customer, merchant, and admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleLifecycleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String token(String email, UserRole role) throws Exception {
        UserCreateDTO reg = UserCreateDTO.builder()
                .email(email).password("password123").name("Test").role(role).build();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());
        return login(email);
    }

    /** Admins cannot self-register, so seed one directly and log in. */
    private String adminToken(String email) throws Exception {
        userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode("password123"))
                .name("Root Admin").role(UserRole.ADMIN).build());
        return login(email);
    }

    private String login(String email) throws Exception {
        LoginRequest login = LoginRequest.builder().email(email).password("password123").build();
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asText();
    }

    private long applyForShop(String merchantToken, String name, double lat, double lng) throws Exception {
        MerchantCreateDTO dto = MerchantCreateDTO.builder()
                .storeName(name).storeType(StoreType.RESTAURANT).address("addr")
                .latitude(lat).longitude(lng).prepTimeMins(10).etaBufferMins(5).build();
        String body = mockMvc.perform(post("/api/merchant/shops").header("Authorization", merchantToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("merchantId").asLong();
    }

    @Test
    void newShopIsPendingAndNotDiscoverableUntilApproved() throws Exception {
        String merchant = token("life-merch@x.com", UserRole.MERCHANT);
        String admin = adminToken("life-admin@x.com");
        long shopId = applyForShop(merchant, "Pending Diner", 41.8781, -87.6298); // Chicago cluster

        String customer = token("life-cust@x.com", UserRole.USER);
        // Not discoverable while pending.
        mockMvc.perform(get("/api/discovery/nearby").header("Authorization", customer)
                        .param("lat", "41.8781").param("lng", "-87.6298").param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Appears in the admin approval queue.
        mockMvc.perform(get("/api/admin/applications").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.merchantId==" + shopId + ")]").exists());

        // Approve, then it becomes discoverable.
        mockMvc.perform(post("/api/admin/shops/" + shopId + "/approve").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/discovery/nearby").header("Authorization", customer)
                        .param("lat", "41.8781").param("lng", "-87.6298").param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void adminCanSuspendAndReactivate() throws Exception {
        String merchant = token("susp-merch@x.com", UserRole.MERCHANT);
        String admin = adminToken("susp-admin@x.com");
        long shopId = applyForShop(merchant, "Suspend Me", 48.8566, 2.3522); // Paris cluster
        mockMvc.perform(post("/api/admin/shops/" + shopId + "/approve").header("Authorization", admin))
                .andExpect(status().isOk());

        String customer = token("susp-cust@x.com", UserRole.USER);
        // Suspend -> hidden from discovery.
        mockMvc.perform(post("/api/admin/shops/" + shopId + "/suspend").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"policy review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/api/discovery/nearby").header("Authorization", customer)
                        .param("lat", "48.8566").param("lng", "2.3522").param("radiusKm", "5"))
                .andExpect(jsonPath("$.length()").value(0));

        // Reactivate -> visible again.
        mockMvc.perform(post("/api/admin/shops/" + shopId + "/reactivate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/discovery/nearby").header("Authorization", customer)
                        .param("lat", "48.8566").param("lng", "2.3522").param("radiusKm", "5"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void oneMerchantCanOwnMultipleShops() throws Exception {
        String merchant = token("multi-merch@x.com", UserRole.MERCHANT);
        applyForShop(merchant, "Shop One", 35.6762, 139.6503);
        applyForShop(merchant, "Shop Two", 35.6800, 139.6600);
        mockMvc.perform(get("/api/merchant/shops").header("Authorization", merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void merchantCannotManageAnotherMerchantsShop() throws Exception {
        String ownerMerchant = token("own-merch@x.com", UserRole.MERCHANT);
        long shopId = applyForShop(ownerMerchant, "Owned", 19.0760, 72.8777);

        String otherMerchant = token("other-merch@x.com", UserRole.MERCHANT);
        mockMvc.perform(get("/api/merchant/shops/" + shopId).header("Authorization", otherMerchant))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotAccessAdminOrMerchantApis() throws Exception {
        String customer = token("nope-cust@x.com", UserRole.USER);
        mockMvc.perform(get("/api/admin/metrics").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/merchant/shops").header("Authorization", customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectingAShopRecordsTheReason() throws Exception {
        String merchant = token("rej-merch@x.com", UserRole.MERCHANT);
        String admin = adminToken("rej-admin@x.com");
        long shopId = applyForShop(merchant, "To Reject", 55.7558, 37.6173);
        mockMvc.perform(post("/api/admin/shops/" + shopId + "/reject").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"incomplete address\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.statusReason").value("incomplete address"));
    }

    @Test
    void adminMetricsReportCounts() throws Exception {
        String admin = adminToken("metrics-admin@x.com");
        mockMvc.perform(get("/api/admin/metrics").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.totalShops").isNumber())
                .andExpect(jsonPath("$.ordersByStatus").exists());
    }

        @Test
        void versionedPaginatedAdminAndMerchantEndpointsWork() throws Exception {
                String merchant = token("page-merch@x.com", UserRole.MERCHANT);
                String admin = adminToken("page-admin@x.com");
                applyForShop(merchant, "Page One", 12.9716, 77.5946);
                applyForShop(merchant, "Page Two", 12.9816, 77.6046);

                mockMvc.perform(get("/api/v1/admin/shops/page").header("Authorization", admin)
                                                .param("status", "PENDING")
                                                .param("page", "0")
                                                .param("size", "1")
                                                .param("sort", "storeName,asc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(1))
                                .andExpect(jsonPath("$.totalElements").isNumber());

                mockMvc.perform(get("/api/v1/merchant/orders/page").header("Authorization", merchant)
                                                .param("page", "0")
                                                .param("size", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }
}
