# OnTheWay — To-Do (execution backlog)

> Derived from [ARCHITECTURE.md](ARCHITECTURE.md). Ordered by value and dependency. Each task is
> independently deliverable. Workflow per task: implement → write and run tests
> (unit/slice/integration) → fix regressions → mark done → document under `docs/`.
>
> Status keys: `[ ]` not started · `[~]` in progress · `[x]` done (tested + documented).

---

## Phase 1 — Role foundation (makes it a product)

### Shop lifecycle & multi-shop
- [x] **T1.1** Add shop **status** (`PENDING`/`APPROVED`/`REJECTED`/`SUSPENDED`) + reason; migration.
- [x] **T1.2** Allow **one owner → many shops** (owner relationship; new shops start `PENDING`).
- [x] **T1.3** Discovery returns **only `APPROVED`, non-suspended** shops.

### Merchant self-service
- [x] **T1.4** `POST /api/merchant/shops` to **apply** for a shop (status `PENDING`).
- [x] **T1.5** `GET /api/merchant/shops` to list the merchant's own shops with status.
- [x] **T1.6** Menu self-service on own shops: add / edit / **change price** / **out-of-stock toggle** / delete.
- [x] **T1.7** Merchant order queue: list and advance orders for own shops only.

### Admin console
- [x] **T1.8** `GET /api/admin/applications` pending queue; **approve** / **reject (reason)**.
- [x] **T1.9** `POST /api/admin/shops/{id}/suspend|reactivate`, `DELETE /api/admin/shops/{id}`.
- [x] **T1.10** `GET /api/admin/metrics` platform overview (counts, orders, revenue, pending, status mix).
- [x] **T1.11** `GET /api/admin/shops` directory with status filter.

### Role-separated frontend
- [x] **T1.12** Role-aware login + landing; guarded routes per role.
- [x] **T1.13** Merchant dashboard (my shops, menu/stock management, order queue, apply for shop).
- [x] **T1.14** Admin dashboard (approvals queue, moderation, metrics).
- [x] **T1.15** Customer dashboard restructured as its own role experience.

## Phase 2 — Vertical selection & powerful search
- [x] **T2.1** Expand category taxonomy (10+ verticals).
- [x] **T2.2** `GET /api/discovery/search`: match shop **and** item names; return item · price · shop · distance.
- [x] **T2.3** Sorting by **distance**, **price**, **relevance**; vertical filter. _(open-now filter deferred.)_
- [x] **T2.4** Customer UI: vertical picker + search bar + sort controls + results list.

## Phase 3 — Live ETA + traffic buffer
- [x] **T3.1** `POST /api/orders/{id}/location` to push customer position while en route.
- [x] **T3.2** **Continuous ETA recompute** on each update; persist updated ready time; re-sync prep start.
- [x] **T3.3** **Traffic-aware buffer window** (earliest–latest), configurable per deployment/shop.
- [x] **T3.4** Customer live-tracking UI showing the moving ETA window.

## Phase 4 — Persistence, portability & scale
- [x] **T4.1** MySQL as the default durable store for real runs; verify migrations on MySQL.
- [x] **T4.2** One-command portable run via Docker compose (develop here, run there).
- [x] **T4.3** Seed **100+ shops across many verticals** for a credible demo.

## Phase 5 — Real-time, payments & hardening (carried over)
- [ ] **T5.1** WebSocket channel for live order status + ETA (replace polling).
- [ ] **T5.2** Real Stripe/Razorpay providers + webhook signature verification; refunds.
- [ ] **T5.3** Money as minor units + currency.
- [ ] **T5.4** Refresh tokens + logout; auth rate limiting.
- [ ] **T5.5** Pagination/sorting on list endpoints; API versioning.

---

### Definition of Done (every task)
1. Code complete, server-authoritative, and secure, consistent with [ARCHITECTURE.md](ARCHITECTURE.md).
2. Tests written and passing (unit + slice/integration as relevant); regression clean.
3. No known defects remaining.
4. Feature documented under `docs/`.
5. To-do checkbox flipped to `[x]`.
