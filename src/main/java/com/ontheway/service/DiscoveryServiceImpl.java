package com.ontheway.service.impl;

import com.ontheway.dto.SearchResultResponse;
import com.ontheway.dto.StoreDiscoveryResponse;
import com.ontheway.fulfillment.GeoPoint;
import com.ontheway.fulfillment.RouteEstimate;
import com.ontheway.fulfillment.RouteProvider;
import com.ontheway.model.Merchant;
import com.ontheway.model.MenuItem;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.repository.MenuItemRepository;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory geo discovery and search: loads approved, located shops (and their items), computes
 * the route from the customer to each, keeps those within the radius, and orders the results.
 *
 * <p>For the expected demo dataset this is simple and fast. At scale this would be replaced by a
 * spatial index and a search index; the {@link DiscoveryService} contract stays the same.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscoveryServiceImpl implements DiscoveryService {

    private final MerchantRepository merchantRepository;
    private final MenuItemRepository menuItemRepository;
    private final RouteProvider routeProvider;

    @Override
    public List<StoreDiscoveryResponse> findNearby(double latitude, double longitude,
                                                   double radiusKm, StoreType storeType) {
        GeoPoint origin = new GeoPoint(latitude, longitude);

        // Only approved, located shops are publicly discoverable.
        List<Merchant> candidates = (storeType == null)
                ? merchantRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(
                        MerchantStatus.APPROVED)
                : merchantRepository.findByStatusAndStoreTypeAndLatitudeIsNotNullAndLongitudeIsNotNull(
                        MerchantStatus.APPROVED, storeType);

        return candidates.stream()
                .map(m -> {
                    RouteEstimate route = routeProvider.estimate(
                            origin, new GeoPoint(m.getLatitude(), m.getLongitude()));
                    return toResponse(m, route);
                })
                .filter(r -> r.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(StoreDiscoveryResponse::getDistanceKm))
                .toList();
    }

    @Override
    public List<SearchResultResponse> search(double latitude, double longitude, double radiusKm,
                                             String query, StoreType storeType, String sort) {
        GeoPoint origin = new GeoPoint(latitude, longitude);
        String q = query == null ? "" : query.trim();

        // Match available items at approved shops by item name or shop name.
        List<MenuItem> items = menuItemRepository.searchAvailableByItemOrShopName(
                MerchantStatus.APPROVED, q);

        List<SearchResultResponse> results = items.stream()
                .map(item -> {
                    Merchant shop = item.getMerchant();
                    if (shop.getLatitude() == null || shop.getLongitude() == null) {
                        return null;
                    }
                    if (storeType != null && shop.getStoreType() != storeType) {
                        return null;
                    }
                    RouteEstimate route = routeProvider.estimate(
                            origin, new GeoPoint(shop.getLatitude(), shop.getLongitude()));
                    double distanceKm = Math.round(route.distanceKm() * 100.0) / 100.0;
                    if (distanceKm > radiusKm) {
                        return null;
                    }
                    return SearchResultResponse.builder()
                            .menuItemId(item.getMenuItemId())
                            .itemName(item.getName())
                            .description(item.getDescription())
                            .price(item.getPrice())
                            .merchantId(shop.getMerchantId())
                            .storeName(shop.getStoreName())
                            .storeType(shop.getStoreType())
                            .address(shop.getAddress())
                            .latitude(shop.getLatitude())
                            .longitude(shop.getLongitude())
                            .distanceKm(distanceKm)
                            .travelMins(route.durationMins())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .sorted(sortComparator(sort, q))
                .toList();

        return results;
    }

    /** Chooses the result ordering: by price, by distance, or by relevance (default). */
    private Comparator<SearchResultResponse> sortComparator(String sort, String query) {
        String mode = sort == null ? "relevance" : sort.trim().toLowerCase();
        return switch (mode) {
            case "price" -> Comparator.comparingDouble(SearchResultResponse::getPrice);
            case "distance" -> Comparator.comparingDouble(SearchResultResponse::getDistanceKm);
            default -> relevanceComparator(query); // "relevance"
        };
    }

    /**
     * Relevance ranking: exact item-name matches first, then name-starts-with, then nearer shops.
     */
    private Comparator<SearchResultResponse> relevanceComparator(String query) {
        String q = query.toLowerCase();
        return Comparator
                .comparingInt((SearchResultResponse r) -> relevanceScore(r, q))
                .thenComparingDouble(SearchResultResponse::getDistanceKm);
    }

    private int relevanceScore(SearchResultResponse r, String q) {
        if (q.isEmpty()) {
            return 2;
        }
        String name = r.getItemName().toLowerCase();
        if (name.equals(q)) {
            return 0;
        }
        if (name.startsWith(q)) {
            return 1;
        }
        if (name.contains(q)) {
            return 2;
        }
        return 3; // matched only on the shop name
    }

    private StoreDiscoveryResponse toResponse(Merchant m, RouteEstimate route) {
        return StoreDiscoveryResponse.builder()
                .merchantId(m.getMerchantId())
                .storeName(m.getStoreName())
                .storeType(m.getStoreType())
                .address(m.getAddress())
                .latitude(m.getLatitude())
                .longitude(m.getLongitude())
                .distanceKm(Math.round(route.distanceKm() * 100.0) / 100.0)
                .travelMins(route.durationMins())
                .prepTimeMins(m.getPrepTimeMins())
                .build();
    }
}
