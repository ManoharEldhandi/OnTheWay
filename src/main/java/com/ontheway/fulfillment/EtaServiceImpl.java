package com.ontheway.fulfillment;

import com.ontheway.exception.BadRequestException;
import com.ontheway.model.Merchant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Default ETA engine.
 *
 * <p>The synchronization rule:
 * <pre>
 *   arrival      = now + travelTime
 *   prepDuration = prepTime + safetyBuffer
 *   prepStartAt  = arrival - prepDuration
 *
 *   if the customer is far enough away (prepStartAt is in the future):
 *       the store waits and starts later  -> readyAt = arrival   (fresh on arrival)
 *   else (customer is close):
 *       the store starts now              -> readyAt = now + prepDuration
 * </pre>
 *
 * A {@link Clock} is injected so tests can control "now".
 */
@Service
public class EtaServiceImpl implements EtaService {

    private final RouteProvider routeProvider;
    private final Clock clock;
    private final int defaultPrepMins;
    private final int defaultBufferMins;
    private final double trafficFactor;
    private final int minTrafficBufferMins;

    public EtaServiceImpl(RouteProvider routeProvider,
                          Clock clock,
                          @Value("${ontheway.eta.default-prep-mins:15}") int defaultPrepMins,
                          @Value("${ontheway.eta.safety-buffer-mins:5}") int defaultBufferMins,
                          @Value("${ontheway.eta.traffic-factor:0.25}") double trafficFactor,
                          @Value("${ontheway.eta.min-traffic-buffer-mins:3}") int minTrafficBufferMins) {
        this.routeProvider = routeProvider;
        this.clock = clock;
        this.defaultPrepMins = defaultPrepMins;
        this.defaultBufferMins = defaultBufferMins;
        this.trafficFactor = trafficFactor;
        this.minTrafficBufferMins = minTrafficBufferMins;
    }

    @Override
    public EtaCalculation estimate(GeoPoint userLocation, Merchant merchant) {
        if (merchant.getLatitude() == null || merchant.getLongitude() == null) {
            throw new BadRequestException(
                    "Store location is not set; ETA cannot be computed for this merchant");
        }

        GeoPoint store = new GeoPoint(merchant.getLatitude(), merchant.getLongitude());
        RouteEstimate route = routeProvider.estimate(userLocation, store);

        int prepTime = merchant.getPrepTimeMins() != null ? merchant.getPrepTimeMins() : defaultPrepMins;
        int buffer = merchant.getEtaBufferMins() != null ? merchant.getEtaBufferMins() : defaultBufferMins;
        int prepDuration = prepTime + buffer;

        // Traffic uncertainty grows with travel time. The buffer is the half-width of the
        // arrival window: longer trips are less predictable, so the window is wider.
        int trafficBuffer = Math.max(minTrafficBufferMins,
                (int) Math.ceil(route.durationMins() * trafficFactor));

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime arrival = now.plusMinutes(route.durationMins());
        LocalDateTime prepStartAt = arrival.minusMinutes(prepDuration);
        LocalDateTime readyAt;

        if (prepStartAt.isBefore(now)) {
            // Customer is closer than the prep time: start now, ready as soon as possible.
            prepStartAt = now;
            readyAt = now.plusMinutes(prepDuration);
        } else {
            // Customer is far enough: hold prep so the order is fresh on arrival.
            readyAt = arrival;
        }

        // Arrival window: expected arrival can come a little early or, allowing for traffic,
        // up to the traffic buffer late.
        LocalDateTime etaEarliest = arrival.minusMinutes(Math.min(trafficBuffer, route.durationMins()));
        LocalDateTime etaLatest = arrival.plusMinutes(trafficBuffer);

        return new EtaCalculation(
                route.distanceKm(), route.durationMins(), trafficBuffer, prepTime, buffer,
                prepStartAt, readyAt, etaEarliest, etaLatest);
    }
}
