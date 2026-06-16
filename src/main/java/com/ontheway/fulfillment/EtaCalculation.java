package com.ontheway.fulfillment;

import java.time.LocalDateTime;

/**
 * The outcome of an ETA synchronization calculation for one order/quote.
 *
 * @param distanceKm    distance from the customer to the store
 * @param travelMins    estimated travel time for the customer
 * @param prepTimeMins  store preparation time used
 * @param bufferMins    safety buffer used
 * @param prepStartAt   the moment the merchant should START preparing
 * @param readyAt       the moment the order will be READY (ideally == arrival)
 */
public record EtaCalculation(
        double distanceKm,
        int travelMins,
        int prepTimeMins,
        int bufferMins,
        LocalDateTime prepStartAt,
        LocalDateTime readyAt
) {
}
