package com.ontheway.fulfillment;

/**
 * The result of a route lookup between two points.
 *
 * @param distanceKm   straight-line or routed distance in kilometres
 * @param durationMins estimated travel time in minutes
 */
public record RouteEstimate(double distanceKm, int durationMins) {
}
