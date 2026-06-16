package com.ontheway.service;

import com.ontheway.dto.StoreDiscoveryResponse;
import com.ontheway.model.enums.StoreType;

import java.util.List;

/**
 * Geo discovery of stores around a customer.
 */
public interface DiscoveryService {

    /**
     * Find located stores within {@code radiusKm} of the given point, optionally filtered
     * by vertical/category, ordered nearest-first and annotated with travel time.
     *
     * @param storeType optional category filter (null = all verticals)
     */
    List<StoreDiscoveryResponse> findNearby(double latitude, double longitude,
                                            double radiusKm, StoreType storeType);
}
