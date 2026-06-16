package com.ontheway.fulfillment;

import com.ontheway.model.Merchant;

/**
 * Core of the "order and get on your way" promise: given the customer's live location
 * and a store, decide when the store should start preparing so the order is ready
 * exactly when the customer arrives.
 */
public interface EtaService {

    /**
     * @param userLocation the customer's current position
     * @param merchant     the store fulfilling the order (must have a location set)
     * @return the synchronized prep-start and ready times plus travel details
     */
    EtaCalculation estimate(GeoPoint userLocation, Merchant merchant);
}
