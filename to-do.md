# OnTheWay — To-Do (execution backlog)

> Derived from `ARCHITECTURE.md`. Ordered by value + risk. Each task is atomic and
> assignable. Workflow per task: **Senior Dev builds → Senior Tester writes/runs tests +
> regression → bugs back to Dev (with Architect) → green → mark done → Senior Doc Writer
> documents** under `docs/`.
>
> Status keys: `[ ]` not started · `[~]` in progress · `[x]` done (tested + documented).

---

## Phase 0 — Stabilize & secure the existing core

- [x] **T0.1** Add **Flyway** + set `ddl-auto=validate`; baseline migration capturing the current schema.
      _(Delivered with `ddl-auto=none` — Flyway is the single source of truth; see ARCHITECTURE ADR-4.)_
- [x] **T0.2** Spring profiles (`dev`/`test`/`prod`); externalize **all secrets** to env; add `.env.example`; remove committed secrets.
- [x] **T0.3** Fix **registration privilege escalation**: drop client `role`; default `USER`; add `LoginRequest` DTO; password policy.
- [x] **T0.4** Fix **JWT filter**: validate signature/expiry **before** parsing claims; map JWT errors to `401` (no `500`).
- [x] **T0.5** Duplicate-email register → clean **`409 Conflict`** (not raw `500`).
- [x] **T0.6** Close **IDORs**: ownership checks on `GET /orders/{id}`, `GET /payments/order/{id}`, order-status update, menu/product update/delete.
- [x] **T0.7** **Order status state-machine** + `ORDER_EVENT` audit; illegal transition → `409`. _(Implemented as `400`.)_
- [x] **T0.8** **Test harness**: JUnit 5 + Mockito + H2/Flyway integration; first unit + slice + integration tests green (27 passing).
- [x] **T0.9** **CORS allowlist** (replace `*`); security headers. _(Auth rate-limiting deferred to a later hardening pass.)_

## Phase 1 — Generalize the domain (multi-vertical)

- [ ] **T1.1** Introduce **`Category`** taxonomy; migrate `StoreType` enum → category data.
- [ ] **T1.2** Introduce **`Store`** with **geo** (lat/lng), open-hours, default prep time; link to Merchant.
- [ ] **T1.3** Rename/generalize **`MenuItem` → `Product`** (backward-compatible migration); keep food semantics as a category.
- [ ] **T1.4** Money to **`BigDecimal`** minor-units + currency across entities, DTOs, totals.
- [ ] **T1.5** **Pagination/sorting/filtering** on all list endpoints.
- [ ] **T1.6** Adopt **MapStruct** mappers to replace hand-written DTO builders.

## Phase 2 — The hero: ETA engine + discovery + realtime

- [ ] **T2.1** **`RouteProvider`** interface + **`MockRouteProvider`** (Haversine ÷ avg speed, clock-injected).
- [ ] **T2.2** **ETA engine**: compute travel time, `readyAt`, `prepStartAt = arrival − prep − buffer`; persist on order.
- [ ] **T2.3** **Scheduler** flips `PLACED → PREPARING` at `prepStartAt` and emits merchant "start now".
- [ ] **T2.4** **Location-driven recompute**: consume customer location updates → recompute ETA.
- [ ] **T2.5** **Discovery API**: nearby (radius), by category, open-now, text search — paginated/filterable.
- [ ] **T2.6** **On-route discovery**: stores within a corridor of the route polyline.
- [ ] **T2.7** **Realtime (STOMP/WebSocket)**: authenticated handshake; order + ETA streams to customer; inbound queue to merchant.

## Phase 3 — Payments productized

- [ ] **T3.1** **`PaymentGateway`** interface + **`MockGateway`** (keyless auto-confirm).
- [ ] **T3.2** Payment **intent/create + confirm**; persist gateway refs; `PAYMENT_PENDING → PLACED` only via server.
- [ ] **T3.3** **Webhook endpoint** with **signature verification** (idempotent) to set final status.
- [ ] **T3.4** **Idempotency keys** + webhook dedup.
- [ ] **T3.5** **Refunds** wired to order cancellation; real **Stripe**/**Razorpay** impls behind config.

## Phase 4 — Prove it (quality & delivery)

- [ ] **T4.1** **Dockerfile** (multi-stage) + **docker-compose** (app + MySQL); one-command up.
- [ ] **T4.2** **CI** (GitHub Actions): build → test → image; coverage gate.
- [ ] **T4.3** **Observability**: Actuator health/readiness, Micrometer metrics, structured logs + correlation id.
- [ ] **T4.4** Regression pass across all modules; performance sanity on discovery/ETA.

## Phase 5 — Show it (frontend, end-to-end)

- [ ] **T5.1** **Frontend scaffold**: React + TS + Vite, router, TanStack Query, API client, auth.
- [ ] **T5.2** **Customer flow**: discovery on map → store → menu → cart → checkout.
- [ ] **T5.3** **Hero screen**: live route + store pin + **ETA-sync countdown** (realtime).
- [ ] **T5.4** **Merchant console**: incoming orders, "start now" prompts, mark ready/picked.
- [ ] **T5.5** **Admin console**: users, stores, categories, oversight.
- [ ] **T5.6** **Seed data** + guided **demo script**; responsive polish.

## Phase 6 — Document & demo assets

- [ ] **T6.1** Make **README truthful**; top-level **docs/** index.
- [ ] **T6.2** Per-feature docs (kept current as each feature lands — the doc-writer step).
- [ ] **T6.3** **API collection** (Postman/Bruno) + architecture diagrams export.

---

### Definition of Done (every task)
1. Code complete, server-authoritative, secure, follows `ARCHITECTURE.md`.
2. Tests written and green (unit + slice/integration as relevant); regression clean.
3. No new bugs from the tester; architect concerns resolved.
4. Feature documented under `docs/`.
5. To-do checkbox flipped to `[x]`.
