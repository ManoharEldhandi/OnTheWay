# Phase 5 — Web Frontend

A **React + TypeScript + Vite** single-page app that makes the whole product clickable —
the end-to-end demo a reviewer can actually use.

## Run it

```bash
# 1) Backend (zero setup, in-memory, seeded)
mvn -s custom-m2/settings.xml spring-boot:run -Dspring-boot.run.profiles=demo
# or: SPRING_PROFILES_ACTIVE=demo java -jar target/OnTheWay-1.0.0.jar

# 2) Frontend (proxies /api -> :8080)
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Demo logins (password `password123`): `alice@ontheway.app` (customer),
`biryani@ontheway.app` (merchant), `admin@ontheway.app` (admin). The login screen has
one-click buttons for these.

## What it demonstrates

### Customer journey (the hero)
1. **Discover** — pick a location (presets or browser geolocation), radius, and category;
   a **dependency-free SVG map** shows you and nearby stores with distance + travel time,
   nearest-first.
2. **Store & menu** — browse items, add to a cart (one store at a time).
3. **Checkout — the hero screen** — a live **ETA-synced pickup** panel: *ready in N min*,
   *store starts preparing now/in N min*, and a travel→prep timeline. This is the
   "order and get on your way" promise made visible.
4. **Order tracking** — a live status timeline (`PLACED → PREPARING → READY → PICKED`)
   that updates from the authenticated WebSocket channel.

### Merchant console
- Live list of incoming orders; advance each through the **legal** lifecycle with one click
  (the UI mirrors the backend state machine), or cancel. Illegal moves are blocked by the API.

## Architecture

```
src/
  api/client.ts        # fetch wrapper: attaches JWT, surfaces backend error messages
  auth/AuthContext.tsx # login/register/logout, loads /api/users/me, token in localStorage
  cart/CartContext.tsx # single-store cart with quantities and total
  components/
    Layout.tsx         # nav shell (role-aware links)
    MapView.tsx        # SVG map projection of lat/lng (no tiles, no API key)
  pages/
    LoginPage, DiscoverPage, StorePage, CheckoutPage, OrderPage, OrdersPage, MerchantPage
  location.ts          # current-location presets + persistence
  types.ts             # DTO types mirroring the backend
```

### Notable decisions
- **No business logic in the client** — it only calls `/api/v1`-style endpoints and renders.
  Prices, totals, ETA, and status are all server-authoritative.
- **Keyless SVG map** instead of a tiled map library: zero API keys, fully offline, reliable
  in any demo environment, and it communicates distance/direction clearly. A real tile map
  can replace `MapView` without touching pages.
- **Authenticated WebSocket updates** reload affected orders, periodically rotate access tokens,
  and reconnect without exposing stale sessions indefinitely.
- **Vite dev proxy** forwards `/api` to `:8080`, so there is no CORS friction and no hardcoded
  backend host in the client.

## Verified end-to-end
Driven in a real browser against the live backend: login → discovery (map + 3 seeded stores)
→ menu → cart → ETA checkout → order placed → tracking; merchant advanced the order
`PLACED → PREPARING → READY → PICKED` with the illegal `PREPARING → PICKED` correctly rejected.

## Build
`npm run build` type-checks (strict TS) and produces an optimized bundle.
