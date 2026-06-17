package com.ontheway.service;

import com.ontheway.dto.*;

import java.util.List;

/**
 * Merchant self-service: applying to open shops and managing the shops the merchant owns.
 * All operations are scoped to the authenticated merchant identified by email.
 */
public interface MerchantService {

    /** Submits a new shop application for the merchant. The shop starts in PENDING status. */
    MerchantResponseDTO applyForShop(String email, MerchantCreateDTO dto);

    /** Lists all shops owned by the merchant, with their current status. */
    List<MerchantResponseDTO> listMyShops(String email);

    /** Returns one of the merchant's own shops (ownership enforced). */
    MerchantResponseDTO getMyShop(String email, Long shopId);

    /** Updates one of the merchant's own shops (ownership enforced). */
    MerchantResponseDTO updateMyShop(String email, Long shopId, MerchantUpdateDTO dto);

    /** Deletes one of the merchant's own shops (ownership enforced). */
    void deleteMyShop(String email, Long shopId);

    /** Returns a shop by id without ownership scoping (for admin and internal use). */
    MerchantResponseDTO getMerchantById(Long merchantId);
}
