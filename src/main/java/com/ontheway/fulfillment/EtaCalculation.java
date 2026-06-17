package com.ontheway.fulfillment;

import java.time.LocalDateTime;

/**
 * The outcome of an ETA synchronization calculation for one order or quote.
 *
 * <p>Because travel time is uncertain (traffic, stops, detours), the arrival is expressed as a
 * window rather than a single instant: {@link #etaEarliest} to {@link #etaLatest}. The window
 * widens with distance and modelled traffic. {@link #readyAt} is the target ready time, and the
 * earliest/latest ready times follow the same window.
 *
 * @param distanceKm         distance from the customer to the store
 * @param travelMins         expected travel time for the customer
 * @param trafficBufferMins  extra minutes added for traffic uncertainty (half-width of the window)
 * @param prepTimeMins       store preparation time used
 * @param bufferMins         store safety buffer used
 * @param prepStartAt        the moment the merchant should START preparing
 * @param readyAt            the target moment the order will be READY
 * @param etaEarliest        earliest plausible arrival
 * @param etaLatest          latest plausible arrival (expected + traffic buffer)
 */
public record EtaCalculation(
        double distanceKm,
        int travelMins,
        int trafficBufferMins,
        int prepTimeMins,
        int bufferMins,
        LocalDateTime prepStartAt,
        LocalDateTime readyAt,
        LocalDateTime etaEarliest,
        LocalDateTime etaLatest
) {
}
