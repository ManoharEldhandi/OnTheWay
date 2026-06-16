package com.ontheway.fulfillment;

import com.ontheway.exception.BadRequestException;
import com.ontheway.model.Merchant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtaServiceImplTest {

    @Mock
    private RouteProvider routeProvider;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

    private EtaServiceImpl service() {
        return new EtaServiceImpl(routeProvider, fixedClock, 15, 5);
    }

    private Merchant storeWithLocation(int prep, int buffer) {
        return Merchant.builder()
                .merchantId(1L).latitude(10.0).longitude(20.0)
                .prepTimeMins(prep).etaBufferMins(buffer).build();
    }

    @Test
    void farCustomer_storeWaits_readyOnArrival() {
        // 60 min away, prep+buffer = 15 -> store starts at 12:45, ready at 13:00 (arrival)
        when(routeProvider.estimate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RouteEstimate(30.0, 60));

        EtaCalculation eta = service().estimate(new GeoPoint(10.5, 20.5), storeWithLocation(10, 5));

        assertThat(eta.readyAt()).isEqualTo(now.plusMinutes(60));
        assertThat(eta.prepStartAt()).isEqualTo(now.plusMinutes(45));
        assertThat(eta.travelMins()).isEqualTo(60);
    }

    @Test
    void nearCustomer_storeStartsNow_customerWaitsMinimum() {
        // 5 min away, prep+buffer = 15 -> can't be ready before 12:15; start now
        when(routeProvider.estimate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RouteEstimate(2.0, 5));

        EtaCalculation eta = service().estimate(new GeoPoint(10.1, 20.1), storeWithLocation(10, 5));

        assertThat(eta.prepStartAt()).isEqualTo(now);
        assertThat(eta.readyAt()).isEqualTo(now.plusMinutes(15));
    }

    @Test
    void merchantWithoutLocation_isRejected() {
        Merchant noGeo = Merchant.builder().merchantId(2L).prepTimeMins(10).etaBufferMins(5).build();
        assertThatThrownBy(() -> service().estimate(new GeoPoint(1, 1), noGeo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("location");
    }
}
