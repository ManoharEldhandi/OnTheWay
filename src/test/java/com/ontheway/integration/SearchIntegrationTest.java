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
 * End-to-end tests for cross-shop product search: matching by item and shop name, vertical
 * filtering, and sorting by price and distance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MerchantRepository merchantRepository;

    // A dedicated cluster (Sydney) so this test's shops are isolated from other suites.
    private static final double LAT = -33.8688;
    private static final double LNG = 151.2093;

    private String token(String email, UserRole role) throws Exception {
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

    private long shop(String token, String name, StoreType type, double lat, double lng) throws Exception {
        return TestFixtures.applyAndApproveShop(mockMvc, objectMapper, merchantRepository, token,
                MerchantCreateDTO.builder().storeName(name).storeType(type).address("addr")
                        .latitude(lat).longitude(lng).prepTimeMins(10).etaBufferMins(5).build());
    }

    private void addItem(String token, long shopId, String name, double price) throws Exception {
        mockMvc.perform(post("/api/menu-items/" + shopId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                MenuItemCreateDTO.builder().name(name).price(price).availability(true).build())))
                .andExpect(status().isCreated());
    }

    @Test
    void search_matchesItemAndShopNames_withSorting() throws Exception {
        String merchant = token("search-merch@x.com", UserRole.MERCHANT);
        long nearShop = shop(merchant, "Sydney Spice", StoreType.RESTAURANT, LAT, LNG);            // 0 km
        long farShop = shop(merchant, "Harbour Biryani Club", StoreType.RESTAURANT, LAT + 0.05, LNG); // ~5.5 km
        addItem(merchant, nearShop, "Chicken Biryani", 300.0);
        addItem(merchant, nearShop, "Veg Pulao", 150.0);
        addItem(merchant, farShop, "Mutton Curry", 250.0); // shop name contains "biryani"

        String customer = token("search-cust@x.com", UserRole.USER);

        // Query "biryani" matches the item "Chicken Biryani" and the shop "Harbour Biryani Club".
        mockMvc.perform(get("/api/discovery/search").header("Authorization", customer)
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .param("radiusKm", "10").param("q", "biryani").param("sort", "relevance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Relevance: the item-name match ranks above the shop-name-only match.
                .andExpect(jsonPath("$[0].itemName").value("Chicken Biryani"))
                .andExpect(jsonPath("$[0].storeName").value("Sydney Spice"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0));
    }

    @Test
    void search_sortsByPriceAndDistance() throws Exception {
        String merchant = token("sortsearch-merch@x.com", UserRole.MERCHANT);
        long shopA = shop(merchant, "Alpha Foods", StoreType.RESTAURANT, 51.5074, -0.1278);         // London, 0 km
        long shopB = shop(merchant, "Beta Foods", StoreType.RESTAURANT, 51.5500, -0.1278);          // ~4.7 km
        addItem(merchant, shopA, "Combo Meal", 400.0);
        addItem(merchant, shopB, "Combo Meal", 200.0);

        String customer = token("sortsearch-cust@x.com", UserRole.USER);

        // Sort by price: cheaper (Beta, 200) first.
        mockMvc.perform(get("/api/discovery/search").header("Authorization", customer)
                        .param("lat", "51.5074").param("lng", "-0.1278")
                        .param("radiusKm", "10").param("q", "combo").param("sort", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].price").value(200.0));

        // Sort by distance: nearer (Alpha, 0 km) first.
        mockMvc.perform(get("/api/discovery/search").header("Authorization", customer)
                        .param("lat", "51.5074").param("lng", "-0.1278")
                        .param("radiusKm", "10").param("q", "combo").param("sort", "distance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storeName").value("Alpha Foods"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.0));
    }

    @Test
    void search_filtersByVertical() throws Exception {
        String merchant = token("vsearch-merch@x.com", UserRole.MERCHANT);
        long restaurant = shop(merchant, "Tokyo Diner", StoreType.RESTAURANT, 35.6762, 139.6503);
        long pharmacy = shop(merchant, "Tokyo Pharmacy", StoreType.PHARMACY, 35.6770, 139.6510);
        addItem(merchant, restaurant, "Ramen", 500.0);
        addItem(merchant, pharmacy, "Ramen-flavoured supplement", 300.0);

        String customer = token("vsearch-cust@x.com", UserRole.USER);
        mockMvc.perform(get("/api/discovery/search").header("Authorization", customer)
                        .param("lat", "35.6762").param("lng", "139.6503")
                        .param("radiusKm", "10").param("q", "ramen").param("category", "PHARMACY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storeType").value("PHARMACY"));
    }
}
