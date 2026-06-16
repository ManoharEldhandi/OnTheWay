package com.ontheway.fulfillment;

/**
 * Estimates travel distance and time between two geographic points.
 *
 * <p>Implementations are swappable via the {@code ontheway.route.provider} property:
 * the default {@link HaversineRouteProvider} is keyless and deterministic (ideal for
 * demos and tests); real traffic-aware providers (Google, Mapbox, OSRM) can be added
 * without touching callers.
 */
public interface RouteProvider {

    RouteEstimate estimate(GeoPoint origin, GeoPoint destination);
}
