# Phase 5 — Web frontend

The React + TypeScript + Vite client presents the complete route-aware pickup product across customer, merchant, and administrator roles.

## Run it

The fastest path starts the API and client together:

```bash
./scripts/start local
```

For separate processes, run the local seeded backend profile and Vite manually:

```bash
mvn -s custom-m2/settings.xml spring-boot:run -Dspring-boot.run.profiles=demo

cd frontend
npm install
npm run dev
```

## Customer journey

1. **Discover** — choose a location, radius, and category. A dependency-free SVG neighborhood map shows nearby shops with travel distance and time; a map pin opens its menu.
2. **Store and menu** — browse available items and build a single-shop cart.
3. **Checkout** — review ETA-synchronized pickup timing: ready-by time, prep start, travel estimate, traffic window, and a route-to-prep explanation.
4. **Order tracking** — after merchant acceptance, the page starts a smooth thirty-second local route simulation. Status updates, ETA checkpoints, and private order chat arrive through the authenticated WebSocket channel.

## Merchant operations

- Paid-order acceptance atomically starts preparation.
- The queue exposes the legal state transition, payment state, customer arrival window, and item manifest.
- Arrival messaging remains operational and privacy-safe: merchants see only ETA-based en-route, approaching, and arrived cues.
- Order chat is available while the order is active; pickup is confirmed with the customer code.

## Client structure

```text
frontend/src/
├── api/              # authenticated fetch wrapper and typed errors
├── auth/             # login, registration, session, and role context
├── cart/             # single-shop cart and totals
├── components/       # navigation, maps, ETA explainer, chat, realtime alerts
├── pages/            # customer, merchant, administrator, and payment surfaces
├── location.ts       # route presets and map coordinates
├── realtime.ts       # authenticated WebSocket lifecycle
└── types.ts          # DTO-aligned TypeScript contracts
```

## Design decisions

- **Server-authoritative operations**: prices, totals, payment state, ETA, and order transitions are calculated and validated by the API.
- **Keyless map rendering**: custom SVG maps keep local development deterministic and API-key-free while still conveying distance, direction, road shape, and live movement.
- **Role-scoped realtime**: each tab maintains an authenticated connection, reconnects automatically, and receives only events it is allowed to access.
- **Responsive Vite proxy**: `/api` and `/ws` proxy to the local backend; `VITE_API_TARGET` makes alternate backend ports explicit without hardcoding hosts.

## Build

```bash
cd frontend
npm run build
```

The command performs a strict TypeScript check and produces an optimized production bundle.
