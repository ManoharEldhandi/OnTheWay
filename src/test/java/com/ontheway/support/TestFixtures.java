package com.ontheway.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontheway.dto.MerchantCreateDTO;
import com.ontheway.model.Merchant;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.repository.MerchantRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for integration tests. Creating a discoverable shop now involves the full
 * lifecycle (a merchant applies, an admin approves), so this centralizes that setup: it applies
 * for a shop through the merchant API and then approves it directly via the repository, which
 * keeps each test focused on the behaviour under test.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    /**
     * Applies for a shop as the given merchant and approves it, returning the new shop id.
     */
    public static long applyAndApproveShop(MockMvc mvc, ObjectMapper om, MerchantRepository shops,
                                           String bearerToken, MerchantCreateDTO dto) throws Exception {
        String body = mvc.perform(post("/api/merchant/shops")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long shopId = om.readTree(body).get("merchantId").asLong();
        approve(shops, shopId);
        return shopId;
    }

    /** Marks a shop approved (the admin action) directly via the repository. */
    public static void approve(MerchantRepository shops, long shopId) {
        Merchant shop = shops.findById(shopId).orElseThrow();
        shop.setStatus(MerchantStatus.APPROVED);
        shops.save(shop);
    }
}
