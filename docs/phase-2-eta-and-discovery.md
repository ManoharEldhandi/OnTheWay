# Phase 2 (part 1) — ETA Engine & Discovery

This is the **hero feature**: the "order and get on your way" promise made real, plus the
map-facing discovery that surrounds it.

## The promise

You pre-order while travelling. The store starts preparing at the *right moment* so your
order is **ready exactly when you arrive** — not early and cold, not late and queued.

## ETA engine

### Inputs
- The customer's **live location** (`latitude`, `longitude`).
- The store's **location** and **preparation time** (`prepTimeMins`), plus a **safety buffer**
  (`etaBufferMins`).

### The synchronization rule
```
arrival      = now + travelTime(customer -> store)
prepDuration = prepTime + safetyBuffer
prepStartAt  = arrival - prepDuration

if prepStartAt is in the future  (customer is far):
    store waits, starts later     -> readyAt = arrival      (fresh on arrival)
else                              (customer is close):
    store starts now              -> readyAt = now + prepDuration
```

### Pluggable routing (`RouteProvider`)
Travel time comes from a `RouteProvider` abstraction:
- **`HaversineRouteProvider`** (default, `ontheway.route.provider=mock`): great-circle distance
  ÷ a configurable average speed. **Keyless and deterministic** — the whole feature runs and
  is tested with no external mapping API.
- The interface supports adding Google, Mapbox, or OSRM adapters without changing callers; those
  external-provider implementations are not included in this repository.

The engine takes an injected `Clock`, so time is controllable in tests.

### Endpoints
- **`POST /api/eta/quote`** — `{ merchantId, latitude, longitude }` → distance, travel minutes,
  prep time, buffer, **`prepStartAt`**, **`readyAt`**. Powers the pre-order countdown.
- **`POST /api/orders`** — if the order includes the customer's `latitude`/`longitude` (and the
  store has a location), the pickup time is **ETA-synchronized** automatically and a human-readable
  `etaSegment` is stored on the order. Otherwise a client `pickupTime` is used. If neither is
  present, the request is rejected.

## Discovery

`GET /api/discovery/nearby?lat={}&lng={}&radiusKm={}&category={}`

Returns located stores **within the radius**, annotated with **distance and travel time**, ordered
**nearest-first**, optionally filtered by **category/vertical** (`RESTAURANT`, `PHARMACY`, `CAFE`, …).
Radius is capped (default max 50 km). This is the data source for the map view.

> The current implementation filters candidates in memory — simple and fast for the demo dataset.
> At scale it is replaced by a bounding-box / spatial-index SQL query behind the same
> `DiscoveryService` contract.

## Multi-vertical

Because discovery and ETA are **category-agnostic**, the same flow already serves a **restaurant**
(food) and a **pharmacy** today, and any other vertical by adding a category. The pre-order +
ETA-synced pickup primitive does not assume "food".

## Data model additions

`merchants` gained (migration `V3__merchant_geo.sql`): `latitude`, `longitude`, `prep_time_mins`
(all nullable so existing rows stay valid).

## Tests (all hermetic, H2 + Flyway)

| Test | Proves |
|---|---|
| `HaversineRouteProviderTest` | Distance/duration math (≈111 km per degree at the equator). |
| `EtaServiceImplTest` | Far customer → store waits, ready on arrival; near customer → start now; no store location → 400. |
| `EtaIntegrationTest` | Quote endpoint; ETA-synced order sets `pickupTime` + `etaSegment`; missing time/location → 400. |
| `DiscoveryIntegrationTest` | Radius filtering, nearest-first ordering, category filter, invalid radius → 400. |

## Configuration

```
ontheway.route.provider=mock            # included adapter: keyless Haversine
ontheway.eta.average-speed-kmph=30
ontheway.eta.safety-buffer-mins=5
ontheway.eta.default-prep-mins=15
```

## Further extensions
- On-route corridor discovery.
- External road-network route providers.

## Auto-advance scheduler (the ETA promise, self-driving) — DONE
`OrderProgressionScheduler` runs every 30 s and moves every `PLACED` order whose computed
`prepStartAt` has arrived into `PREPARING` — so the store starts preparing at exactly the
right moment, automatically; an audit event records `system:scheduler` as the actor.
`prepStartAt` is persisted at placement (migration `V5`); the core scan `advanceDueOrders()`
takes an injected `Clock` for deterministic tests (`OrderProgressionSchedulerTest`) and emits the
same realtime status event as a manual merchant transition.
