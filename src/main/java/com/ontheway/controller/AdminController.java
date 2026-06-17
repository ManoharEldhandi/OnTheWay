package com.ontheway.controller;

import com.ontheway.dto.AdminMetricsResponse;
import com.ontheway.dto.MerchantResponseDTO;
import com.ontheway.dto.ModerationReasonDTO;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrator console API: shop moderation and platform metrics. Every endpoint requires the
 * ADMIN role.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** Shops awaiting review. */
    @GetMapping("/applications")
    public ResponseEntity<List<MerchantResponseDTO>> pendingApplications() {
        return ResponseEntity.ok(adminService.listPendingShops());
    }

    /** All shops, optionally filtered by status (e.g. {@code ?status=SUSPENDED}). */
    @GetMapping("/shops")
    public ResponseEntity<List<MerchantResponseDTO>> shops(
            @RequestParam(value = "status", required = false) MerchantStatus status) {
        return ResponseEntity.ok(adminService.listShops(status));
    }

    @PostMapping("/shops/{shopId}/approve")
    public ResponseEntity<MerchantResponseDTO> approve(@PathVariable("shopId") Long shopId) {
        return ResponseEntity.ok(adminService.approveShop(shopId));
    }

    @PostMapping("/shops/{shopId}/reject")
    public ResponseEntity<MerchantResponseDTO> reject(
            @PathVariable("shopId") Long shopId, @Valid @RequestBody ModerationReasonDTO body) {
        return ResponseEntity.ok(adminService.rejectShop(shopId, body.getReason()));
    }

    @PostMapping("/shops/{shopId}/suspend")
    public ResponseEntity<MerchantResponseDTO> suspend(
            @PathVariable("shopId") Long shopId, @Valid @RequestBody ModerationReasonDTO body) {
        return ResponseEntity.ok(adminService.suspendShop(shopId, body.getReason()));
    }

    @PostMapping("/shops/{shopId}/reactivate")
    public ResponseEntity<MerchantResponseDTO> reactivate(@PathVariable("shopId") Long shopId) {
        return ResponseEntity.ok(adminService.reactivateShop(shopId));
    }

    @DeleteMapping("/shops/{shopId}")
    public ResponseEntity<Void> delete(@PathVariable("shopId") Long shopId) {
        adminService.deleteShop(shopId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics")
    public ResponseEntity<AdminMetricsResponse> metrics() {
        return ResponseEntity.ok(adminService.metrics());
    }
}
