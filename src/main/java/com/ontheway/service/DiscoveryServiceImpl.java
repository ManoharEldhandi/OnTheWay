package com.ontheway.service.impl;

import com.ontheway.dto.StoreDiscoveryResponse;
import com.ontheway.fulfillment.GeoPoint;
import com.ontheway.fulfillment.RouteEstimate;
import com.ontheway.fulfillment.RouteProvider;
import com.ontheway.model.Merchant;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory geo discovery: loads located, approved shops (optionally by category), computes the
 * route from the customer to each, keeps those within the radius, and orders nearest-first.
 *
 * <p>For the expected demo dataset this is simple and fast. At scale this would be replaced
 * by a spatial index / bounding-box SQL query; the {@link DiscoveryService} contract stays
 * the same.
 */
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

    private final MerchantRepository merchantRepository;
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
