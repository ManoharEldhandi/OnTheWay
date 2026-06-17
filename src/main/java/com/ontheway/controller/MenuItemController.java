package com.ontheway.controller;

import com.ontheway.dto.*;
import com.ontheway.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/menu-items", "/api/v1/menu-items"})
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    /**
     * Add a new menu item under a specific merchant ID.
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/{merchantId}")
    public ResponseEntity<MenuItemResponseDTO> addMenuItem(
            Authentication auth,
            @PathVariable("merchantId") Long merchantId,
            @Valid @RequestBody MenuItemCreateDTO dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.addMenuItem(merchantId, dto, auth.getName()));
    }

    /**
     * Update menu item by item ID, e.g. price/availability.
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponseDTO> updateMenuItem(
            Authentication auth,
            @PathVariable("id") Long id,
            @Valid @RequestBody MenuItemUpdateDTO dto) {

        return ResponseEntity.ok(menuItemService.updateMenuItem(id, dto, auth.getName()));
    }

    /**
     * Delete menu item by ID.
     */
    @PreAuthorize("hasRole('MERCHANT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMenuItem(Authentication auth, @PathVariable("id") Long id) {
        menuItemService.deleteMenuItem(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * View a single menu item by its ID.
     */
    @PreAuthorize("hasRole('MERCHANT') or hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponseDTO> getMenuItem(@PathVariable("id") Long id) {
        return ResponseEntity.ok(menuItemService.getMenuItemById(id));
    }

    /**
     * View all menu items for a specific merchant.
     */
    @PreAuthorize("hasRole('MERCHANT') or hasRole('USER') or hasRole('ADMIN')")
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<List<MenuItemResponseDTO>> getMenuItemsByMerchant(
            @PathVariable("merchantId") Long merchantId) {

        return ResponseEntity.ok(menuItemService.getMenuItemsByMerchant(merchantId));
    }
}
