package com.ontheway.service;

import com.ontheway.dto.*;

public interface MerchantService {
    /** Registers a merchant profile for the authenticated user identified by email. */
    MerchantResponseDTO registerMerchant(String email, MerchantCreateDTO dto);

    MerchantResponseDTO getMerchantById(Long merchantId);
    MerchantResponseDTO updateMerchant(Long merchantId, MerchantUpdateDTO dto);
    void deleteMerchant(Long merchantId);
}
