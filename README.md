# OnTheWay

> Route-Aware Pickup & Reservation Platform

**OnTheWay** removes the wait. You pre-order from any nearby shop while you're *on the way*,
and your order is **ready exactly when you arrive** — synchronized to your live ETA. Walk in,
pick up, go. No queue, no idle wait, nothing prepared too early.

It is **not** food-only. The same primitive — *pre-order + ETA-synced pickup* — serves
restaurants, pharmacies, grocery, retail, cafés, and many more verticals.

> Originally conceived as a smart pickup feature for navigation apps (e.g. Google Maps), built
> here as a complete, demoable product: a secure Spring Boot backend and a React web client.

---

## Why it's different

The hero is the **ETA-synchronization engine**: the store starts preparing at the *right
moment* so your order is fresh and ready the second you reach the door.

```
arrival      = now + travelTime(you → store)
prepDuration = prepTime + safetyBuffer
prepStartAt  = arrival − prepDuration   →  ready exactly on arrival
```

Travel time comes from a pluggable `RouteProvider` (a keyless Haversine mock by default;
Google/Mapbox/OSRM drop in via config), so the whole product runs and is tested with **no API
keys**.

---

## Quick start

### Option A — zero setup (in-memory, seeded demo)

```bash
# Backend (Java 17) — H2 + Flyway + demo data, no database to install
mvn -s custom-m2/settings.xml spring-boot:run -Dspring-boot.run.profiles=demo

# Frontend
cd frontend && npm install && npm run dev      # http://localhost:5173
```

Demo logins (password `password123`, one-click on the login screen):
`alice@ontheway.app` (customer), `biryani@ontheway.app` (merchant — runs multiple shops),
`admin@ontheway.app` (admin). The seed loads **115 shops / 507 items** across verticals,
including pending and suspended shops so the admin console has something to moderate.

### Option B — full stack with Docker (MySQL), one command

Develop on one machine, run on any other with only Docker installed:

```bash
docker compose up --build
# frontend  → http://localhost:5173   (nginx, proxies /api to the backend)
# backend   → http://localhost:8080   (dev profile, MySQL)
# MySQL     → localhost:3306          (persistent named volume)
```

The backend applies Flyway migrations on startup and MySQL data survives restarts in a named
volume, so the whole product is portable and durable across machines.

API docs (Swagger UI): http://localhost:8080/swagger-ui.html ·
Health: http://localhost:8080/actuator/health

For full setup, configuration, and a guided walkthrough, see [docs/USAGE.md](docs/USAGE.md).

### Option C — event-driven search platform (Kafka + Elasticsearch)

The optional platform overlay preserves MySQL as the transactional source of truth while adding
Kafka for keyed order events and Elasticsearch for indexed catalog search:

```bash
docker compose -f docker-compose.yml -f docker-compose.platform.yml up --build
# GraphQL → POST http://localhost:8080/graphql
# Kafka   → localhost:9092 (topic: ontheway.order-events)
# Search  → Elasticsearch at localhost:9200
```

GraphQL exposes authenticated catalog search and an admin-only reindex mutation. If the optional
services are disabled, the same GraphQL contract falls back to MySQL/JPA, which keeps local tests
and zero-infrastructure demos deterministic. Kubernetes manifests and deployment guidance live in
[k8s/README.md](k8s/README.md).

---

## What works today

- **Three real roles, three dashboards**: customers discover & order; **merchants** run their own
  shops (add/edit/remove items, set price, mark out-of-stock, open more shops); **admins** moderate
  the marketplace (approve/reject/suspend/delete shops, ban, view live metrics).
- **Shop lifecycle**: a new shop is `PENDING` until an admin approves it; one owner can run many
  shops; only `APPROVED` shops are publicly visible and orderable.
- **Auth**: JWT (validate-before-parse), BCrypt, role-based + ownership-checked authorization.
- **Powerful search**: `GET /api/discovery/search` across **item *and* shop names**, returning the
  item, its price, the shop, and the distance — sortable by **price, distance, or relevance**, and
  filterable by vertical. Plus `GET /api/discovery/nearby` (radius + category, nearest-first).
- **Live, traffic-aware ETA**: the store starts preparing timed to your arrival; while en route the
  customer streams location (`POST /api/orders/{id}/location`) and sees an Uber-style **arrival
  window** (earliest–latest) that updates as they move and widens with traffic.
- **Ordering**: server-authoritative validation, a real status **state machine**
  (`PLACED → PREPARING → READY → PICKED`, `→ CANCELLED`), and an immutable audit trail.
- **Payments**: a gateway abstraction with a keyless mock provider by default, plus Stripe and
  Razorpay adapters, idempotency controls, signed-webhook verification, refunds, minor currency
  units, ownership-checked flows, and traceable payment/order state transitions.
- **GraphQL + Elasticsearch**: authenticated catalog queries over item, shop, and vertical fields;
  admin-controlled reindexing from the relational source of truth; MySQL fallback by configuration.
- **Kafka event stream**: order placement, status, and ETA events are keyed by order ID for ordered,
  replayable downstream risk monitoring, operational analytics, and control evidence.
- **Catalog**: stores (with geo + prep time) and menu items across **16 verticals** (restaurant,
  pharmacy, grocery, café, hotel, electronics, …).
- **Web app**: role-aware routing → vertical picker + search + map → menu → cart → ETA checkout →
  live tracking; merchant & admin consoles; a Swiss, grid-driven black/white/yellow interface.
- **Engineering**: Flyway migrations, profiles (`dev`/`test`/`prod`/`demo`), externalized
  secrets, a hermetic test suite (H2 + real migrations) **plus a real-MySQL migration test**
  (Testcontainers), a **72-test suite**, a 1,000-user load test, Docker, Kubernetes + CI.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2, REST + GraphQL, Spring Security (JWT), Spring Data JPA |
| Data & messaging | MySQL, Elasticsearch, Kafka, WebSockets, **Flyway** migrations |
| Frontend | React 18, TypeScript, Vite |
| Tooling | Maven, JUnit 5, Mockito, MockMvc, Docker, Kubernetes, GitHub Actions |

---

## Project layout

```
.
├── ARCHITECTURE.md         # system design (C4 views, ADRs, roadmap)
├── to-do.md                # ordered backlog and progress tracker
├── docs/                   # usage guide, stress testing, and per-feature docs
├── src/main/java/com/ontheway/
│   ├── controller/  service/  repository/  model/  dto/  exception/  security/
│   ├── fulfillment/        # RouteProvider + ETA engine + scheduler (the differentiator)
│   ├── payment/            # PaymentGateway abstraction + mock provider
│   ├── realtime/           # WebSocket broadcasts + optional Kafka order-event publisher
│   ├── search/             # Elasticsearch search + relational fallback
│   └── config/             # CORS, Swagger, app beans, demo seeder
├── src/main/resources/
│   ├── application*.yml     # profile configuration
│   └── db/migration/        # Flyway V1..V8 migrations for lifecycle, auth, and money canonicalization
├── src/test/java/...        # unit, slice, integration, and load tests
├── frontend/                # React + TS + Vite web client
├── k8s/                     # Kubernetes deployment, services, config, and secret example
├── Dockerfile  docker-compose*.yml  .github/workflows/ci.yml
└── pom.xml
```

---

## Build & test

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)"
mvn -s custom-m2/settings.xml clean test     # hermetic suite (no Docker/MySQL needed)
cd frontend && npm run build                 # strict type-check + production bundle
```

For the 1,000-user load test, see [docs/STRESS_TESTING.md](docs/STRESS_TESTING.md).

---

## Documentation

- [docs/USAGE.md](docs/USAGE.md) — how to set up, run, and use the platform.
- [ARCHITECTURE.md](ARCHITECTURE.md) — system design and rationale.
- [docs/STRESS_TESTING.md](docs/STRESS_TESTING.md) — load testing guide.
- [to-do.md](to-do.md) — backlog and progress.
- Per-feature docs:
  [phase-0](docs/phase-0-stabilization-and-security.md),
  [phase-2 (ETA + discovery)](docs/phase-2-eta-and-discovery.md),
  [phase-3 (payments)](docs/phase-3-payments.md),
  [live ETA & traffic window](docs/live-eta-tracking.md),
  [persistence & portability](docs/persistence-and-portability.md),
  [product UI redesign](docs/product-ui-redesign.md),
  [phase-5 realtime/payments/hardening](docs/phase-5-realtime-payments-hardening.md),
  [phase-5 (frontend)](docs/phase-5-frontend.md).

---

## License

Copyright (c) 2025 Manohar Eldhandi. All rights reserved. Provided "as is", without warranty
of any kind.
