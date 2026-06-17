package com.ontheway.service;

import com.ontheway.dto.SearchResultResponse;
import com.ontheway.dto.StoreDiscoveryResponse;
import com.ontheway.model.enums.StoreType;

import java.util.List;

/**
 * Geo discovery and search of shops and their items around a customer.
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

    /**
     * Search available items at approved shops within range, matching the query against both item
     * names and shop names. Each result carries the item, its price, the shop, and the distance.
     *
     * @param query     text to match (item name or shop name); blank returns items by proximity
     * @param storeType optional vertical filter (null = all)
     * @param sort      one of {@code distance}, {@code price}, {@code relevance}
     */
    List<SearchResultResponse> search(double latitude, double longitude, double radiusKm,
                                      String query, StoreType storeType, String sort);
}
