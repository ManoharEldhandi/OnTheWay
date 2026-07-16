package com.ontheway.fulfillment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HaversineRouteProviderTest {

    private final HaversineRouteProvider provider = new HaversineRouteProvider(30.0);

    @Test
    void distanceBetweenOneDegreeOfLongitudeAtEquatorIsAbout111Km() {
        double km = HaversineRouteProvider.haversineKm(new GeoPoint(0, 0), new GeoPoint(0, 1));
        assertThat(km).isBetween(110.0, 112.0);
    }

    @Test
    void samePointHasZeroDistanceAndDuration() {
        RouteEstimate est = provider.estimate(new GeoPoint(12.9, 77.6), new GeoPoint(12.9, 77.6));
        assertThat(est.distanceKm()).isEqualTo(0.0);
        assertThat(est.durationMins()).isEqualTo(0);
    }

    @Test
    void durationScalesWithDistanceAndSpeed() {
        // ~111 km at 30 km/h => ~222 minutes
        RouteEstimate est = provider.estimate(new GeoPoint(0, 0), new GeoPoint(0, 1));
        assertThat(est.durationMins()).isBetween(220, 224);
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 77.6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(12.9, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
