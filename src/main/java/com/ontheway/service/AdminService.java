package com.ontheway.service;

import com.ontheway.dto.AdminMetricsResponse;
import com.ontheway.dto.MerchantResponseDTO;
import com.ontheway.model.enums.MerchantStatus;

import java.util.List;

/**
 * Administrator operations: shop moderation and platform metrics. All methods are intended to be
 * called only by users with the ADMIN role (enforced at the controller).
 */
public interface AdminService {

    /** Shops awaiting review (the approval queue). */
    List<MerchantResponseDTO> listPendingShops();

    /** All shops, optionally filtered by status. */
    List<MerchantResponseDTO> listShops(MerchantStatus status);

    /** Approve a pending shop so it becomes discoverable. */
    MerchantResponseDTO approveShop(Long shopId);

    /** Reject a pending shop, recording the reason. */
    MerchantResponseDTO rejectShop(Long shopId, String reason);

    /** Suspend an approved shop, hiding it from discovery, recording the reason. */
    MerchantResponseDTO suspendShop(Long shopId, String reason);

    /** Reactivate a suspended shop. */
    MerchantResponseDTO reactivateShop(Long shopId);

    /** Permanently delete a shop. */
    void deleteShop(Long shopId);

    /** Platform overview metrics. */
    AdminMetricsResponse metrics();
}
