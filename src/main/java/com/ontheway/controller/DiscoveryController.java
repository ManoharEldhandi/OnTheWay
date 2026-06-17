package com.ontheway.controller;

import com.ontheway.dto.SearchResultResponse;
import com.ontheway.dto.StoreDiscoveryResponse;
import com.ontheway.exception.BadRequestException;
import com.ontheway.model.enums.StoreType;
import com.ontheway.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Store discovery and search around the customer (the customer dashboard's data source).
 */
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private static final double MAX_RADIUS_KM = 50.0;

    private final DiscoveryService discoveryService;

    /**
     * Find nearby stores. Example:
     * {@code GET /api/discovery/nearby?lat=12.97&lng=77.59&radiusKm=5&category=PHARMACY}
     */
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @GetMapping("/nearby")
    public ResponseEntity<List<StoreDiscoveryResponse>> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "radiusKm", defaultValue = "5") double radiusKm,
            @RequestParam(value = "category", required = false) StoreType category) {

        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new BadRequestException("radiusKm must be between 0 and " + MAX_RADIUS_KM);
        }
        return ResponseEntity.ok(discoveryService.findNearby(lat, lng, radiusKm, category));
    }

    /**
     * Search items across shops by item name or shop name. Each result carries the item, its
     * price, the shop, and the distance. Example:
     * {@code GET /api/discovery/search?lat=12.97&lng=77.59&radiusKm=10&q=biryani&sort=price}
     */
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<List<SearchResultResponse>> search(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "radiusKm", defaultValue = "10") double radiusKm,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "category", required = false) StoreType category,
            @RequestParam(value = "sort", defaultValue = "relevance") String sort) {

        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new BadRequestException("radiusKm must be between 0 and " + MAX_RADIUS_KM);
        }
        return ResponseEntity.ok(discoveryService.search(lat, lng, radiusKm, query, category, sort));
    }
}
