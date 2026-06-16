# OnTheWay — order ahead, arrive, pick up, get on your way

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
`alice@ontheway.app` (customer), `biryani@ontheway.app` (merchant), `admin@ontheway.app`.

### Option B — full stack with Docker (MySQL)

```bash
docker compose up --build        # backend on :8080, MySQL on :3306 (dev profile)
```

API docs (Swagger UI): http://localhost:8080/swagger-ui.html ·
Health: http://localhost:8080/actuator/health

For full setup, configuration, and a guided walkthrough, see [docs/USAGE.md](docs/USAGE.md).

---

## What works today

- **Auth**: JWT (validate-before-parse), BCrypt, role-based + ownership-checked authorization.
- **ETA engine**: `POST /api/eta/quote`; orders accept a live location and are ETA-synchronized;
  a scheduler starts preparation automatically at the computed time.
- **Discovery**: `GET /api/discovery/nearby` — radius + category, nearest-first, travel-time annotated.
- **Ordering**: server-authoritative validation, a real status **state machine**
  (`PLACED → PREPARING → READY → PICKED`, `→ CANCELLED`), and an immutable audit trail.
- **Payments**: a gateway abstraction with a keyless mock provider; idempotent, ownership-checked.
- **Catalog**: stores (with geo + prep time) and menu items across multiple verticals.
- **Web app**: discovery on a map → menu → cart → ETA checkout → live order tracking, plus a
  merchant console; a grey-and-silver, grid-based interface.
- **Engineering**: Flyway migrations, profiles (`dev`/`test`/`prod`/`demo`), externalized
  secrets, a hermetic test suite (H2 + real migrations), a 1,000-user load test, Docker + CI.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2, Spring Security (JWT), Spring Data JPA |
| Database | MySQL (dev/prod), H2 (test/demo), **Flyway** migrations |
| Frontend | React 18, TypeScript, Vite |
| Tooling | Maven, JUnit 5, Mockito, MockMvc, Docker, GitHub Actions |

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
│   └── config/             # CORS, Swagger, app beans, demo seeder
├── src/main/resources/
│   ├── application*.yml     # profile configuration
│   └── db/migration/        # Flyway V1..V5
├── src/test/java/...        # unit, slice, integration, and load tests
├── frontend/                # React + TS + Vite web client
├── Dockerfile  docker-compose.yml  .github/workflows/ci.yml
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
  [phase-5 (frontend)](docs/phase-5-frontend.md).

---

## License

Copyright (c) 2025 Manohar Eldhandi. All rights reserved. Provided "as is", without warranty
of any kind.
