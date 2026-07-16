# Live ETA & Traffic-Aware Arrival Window

This builds on the [ETA engine](phase-2-eta-and-discovery.md). The original engine produced a
**single** ready time from one position snapshot taken at order time. This feature makes the ETA
**live** — it follows the customer as they move — and replaces the brittle single instant with an
**arrival window** that accounts for traffic, the way Uber/Ola/Google Maps do.

## Why a window, not a single time

Travel time is uncertain: traffic, stops, and detours all shift it. Committing the kitchen to one
exact minute is fragile. Instead the engine reports:

```
etaEarliest  ──────  etaLatest
        \            /
         expected arrival
```

The half-width of that window is the **traffic buffer**. It widens with distance, because longer
trips are less predictable.

### How the buffer is computed
```
trafficBuffer = max(minTrafficBufferMins, ceil(travelMins * trafficFactor))
etaEarliest   = arrival - min(trafficBuffer, travelMins)
etaLatest     = arrival + trafficBuffer
```

The synchronization rule for `prepStartAt` / `readyAt` is unchanged — the store still times prep to
the expected arrival so the order is fresh, not early-and-cold or late-and-queued.

### Configuration (per deployment)
| Property | Env var | Default | Meaning |
| --- | --- | --- | --- |
| `ontheway.eta.scheduler-enabled` | `ETA_SCHEDULER_ENABLED` | `true` | Enables automatic `PLACED` → `PREPARING` progression. Disable for deterministic test runs. |
| `ontheway.eta.traffic-factor` | `ETA_TRAFFIC_FACTOR` | `0.25` | Fraction of travel time added as uncertainty (window half-width). |
| `ontheway.eta.min-traffic-buffer-mins` | `ETA_MIN_TRAFFIC_BUFFER_MINS` | `3` | Floor for the buffer, so even short trips get a small cushion. |

## Live location updates

While the customer is **en route** (order status `PLACED` or `PREPARING`), their app streams
position updates. Each update recomputes the ETA and re-syncs the order.

### Endpoint
**`POST /api/orders/{id}/location`** — body `{ latitude, longitude }`.

- **Role**: `USER` only.
- **Ownership**: only the customer who placed the order may post for it (returns `403` otherwise —
  an IDOR guard).
- **Lifecycle**: rejected with `400` once the order is `READY`, `PICKED`, or `CANCELLED` — there is
  nothing left to track.

On success it:
1. Records the position as a `Location` ping (history / future analytics).
2. Recomputes the ETA from the new position.
3. Persists the updated `pickupTime` (= ready time), `prepStartAt`, and a human-readable
   `etaSegment` on the order, keeping the kitchen in sync as the customer moves.
4. Returns the full ETA quote, including `trafficBufferMins`, `etaEarliest`, and `etaLatest`.

### Quote response additions
`POST /api/eta/quote` and the live endpoint now return three extra fields:

| Field | Meaning |
| --- | --- |
| `trafficBufferMins` | Half-width of the arrival window. |
| `etaEarliest` | Earliest plausible arrival. |
| `etaLatest` | Latest plausible arrival (expected + traffic buffer). |

## Customer experience

On the order page, the customer taps **Share my live location**. The browser Geolocation API
(`watchPosition`) streams their position; the page shows a moving **arrival window**
(`etaEarliest – etaLatest`), the travel time, the traffic buffer, and the synced ready time. The
checkout quote shows the same window up front. Sharing stops automatically once the order is ready.

> Location is shared only while the order is active and only with the customer's explicit consent;
> it is never streamed in the background.

## Tests

- **Unit** (`EtaServiceImplTest`): asserts the window math — for a 60-minute trip with
  `trafficFactor=0.25`, the buffer is 15 minutes and the window is `arrival ± 15`.
- **Integration** (`EtaIntegrationTest`):
  - the quote response includes the traffic-aware window fields;
  - a position update recomputes the ETA and returns the window;
  - a different customer cannot post location for someone else's order (`403`).
