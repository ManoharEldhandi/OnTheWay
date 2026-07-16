package com.ontheway.controller;

import com.ontheway.dto.*;
import com.ontheway.service.MerchantService;
import com.ontheway.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Merchant self-service API. Every operation is scoped to the authenticated merchant: a merchant
 * can apply to open shops, manage the shops they own, and see the orders placed at those shops.
 */
@RestController
@RequestMapping({"/api/merchant", "/api/v1/merchant"})
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;
    private final OrderService orderService;

    /** Submit a new shop application. The shop starts in PENDING status until an admin approves it. */
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/shops")
    public ResponseEntity<MerchantResponseDTO> applyForShop(
            Authentication auth, @Valid @RequestBody MerchantCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(merchantService.applyForShop(auth.getName(), dto));
    }

    /** List the merchant's own shops with their current lifecycle status. */
    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/shops")
    public ResponseEntity<List<MerchantResponseDTO>> myShops(Authentication auth) {
        return ResponseEntity.ok(merchantService.listMyShops(auth.getName()));
    }

    /** Get one of the merchant's own shops. */
    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/shops/{shopId}")
    public ResponseEntity<MerchantResponseDTO> myShop(
            Authentication auth, @PathVariable("shopId") Long shopId) {
        return ResponseEntity.ok(merchantService.getMyShop(auth.getName(), shopId));
    }

    /** Update one of the merchant's own shops (profile, location, prep time, ETA buffer). */
    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/shops/{shopId}")
    public ResponseEntity<MerchantResponseDTO> updateShop(
            Authentication auth, @PathVariable("shopId") Long shopId,
            @Valid @RequestBody MerchantUpdateDTO dto) {
        return ResponseEntity.ok(merchantService.updateMyShop(auth.getName(), shopId, dto));
    }

    /** Delete one of the merchant's own shops. */
    @PreAuthorize("hasRole('MERCHANT')")
    @DeleteMapping("/shops/{shopId}")
    public ResponseEntity<Void> deleteShop(
            Authentication auth, @PathVariable("shopId") Long shopId) {
        merchantService.deleteMyShop(auth.getName(), shopId);
        return ResponseEntity.noContent().build();
    }

    /** The merchant's order queue: every order across all of the merchant's shops. */
    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponseDTO>> myOrders(Authentication auth) {
        return ResponseEntity.ok(orderService.getOrdersForOwner(auth.getName()));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/orders/page")
    public ResponseEntity<PageResponse<OrderResponseDTO>> myOrdersPage(
            Authentication auth,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.from(orderService.getOrdersForOwner(auth.getName(), pageable))
        );
    }
}
