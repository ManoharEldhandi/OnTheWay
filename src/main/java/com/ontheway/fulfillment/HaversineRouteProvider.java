package com.ontheway.fulfillment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Keyless, deterministic {@link RouteProvider} based on the Haversine great-circle
 * distance divided by a configurable average speed. Active by default
 * ({@code ontheway.route.provider=mock}); it lets the entire ETA feature run — and be
 * tested — without any external mapping API.
 */
@Component
@ConditionalOnProperty(name = "ontheway.route.provider", havingValue = "mock", matchIfMissing = true)
public class HaversineRouteProvider implements RouteProvider {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final double averageSpeedKmph;

    public HaversineRouteProvider(
            @Value("${ontheway.eta.average-speed-kmph:30}") double averageSpeedKmph) {
        this.averageSpeedKmph = averageSpeedKmph > 0 ? averageSpeedKmph : 30;
    }

    @Override
    public RouteEstimate estimate(GeoPoint origin, GeoPoint destination) {
        double distanceKm = haversineKm(origin, destination);
        int durationMins = (int) Math.ceil(distanceKm / averageSpeedKmph * 60.0);
        return new RouteEstimate(distanceKm, Math.max(durationMins, 0));
    }

    static double haversineKm(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return EARTH_RADIUS_KM * c;
    }
}
