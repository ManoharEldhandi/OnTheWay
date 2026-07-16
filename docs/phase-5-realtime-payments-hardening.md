# Phase 5 — Realtime, Payments, Money, Auth, and API Hardening

## Realtime order updates

- Native WebSocket endpoint: `GET /ws/orders?token={accessToken}`.
- Handshake is JWT-authenticated by `OrderWebSocketAuthInterceptor`.
- Expired access-token sessions are closed before any further event is delivered, and the
  frontend rotates its token and reconnects periodically.
- Events are scoped so customers see their own orders, merchants see orders for shops they own,
  and admins can observe all order events.
- Events are emitted on:
  - order placement (`ORDER_PLACED`),
  - merchant status changes (`ORDER_STATUS_CHANGED`),
  - live ETA/location changes (`ORDER_ETA_CHANGED`).
- Frontend order screens use the socket to reload relevant order data instead of 4-second polling.

## Payments

- Mock gateway remains default and keyless.
- Stripe and Razorpay gateway adapters are now available behind `ontheway.payment.provider`.
- Webhooks:
  - `POST /api/payments/webhook/{gateway}` is public, but provider signatures are verified by the active gateway.
  - Mock webhook verification uses `X-Mock-Signature: mock` for deterministic tests.
- Refunds:
  - `POST /api/payments/{paymentId}/refund` is admin-only.
  - Only completed payments can be refunded.
  - Refunded payments move to `REFUNDED`.

## Money

Existing decimal fields are retained for compatibility, but canonical fields were added:

- `priceMinor` / `currency` on menu items.
- `priceEachMinor`, `totalPriceMinor`, `currency` on order items.
- `totalAmountMinor` / `currency` on orders.
- `amountMinor` / `currency` on payments.

Flyway `V8__money_minor_units.sql` backfills existing rows from decimal values.

## Auth

- Login returns the legacy `token` plus explicit `accessToken`, `refreshToken`, token type, and TTL.
- `POST /api/auth/refresh` rotates refresh tokens and revokes the previous one.
- `POST /api/auth/logout` revokes the refresh token.
- Refresh tokens are stored hashed in `refresh_tokens` (`V7__refresh_tokens.sql`).
- Auth rate limiting applies to login/register/refresh and is configurable via
  `ontheway.security.rate-limit.auth-requests-per-minute`.

## Pagination and versioning

Existing list endpoints still return arrays for current clients. Additive endpoints provide pagination:

- `GET /api/v1/admin/shops/page?page=&size=&sort=&status=`
- `GET /api/v1/merchant/orders/page?page=&size=&sort=`

Version aliases now mirror the main route families:

- `/api/v1/auth`
- `/api/v1/users`
- `/api/v1/admin`
- `/api/v1/merchant`
- `/api/v1/orders`
- `/api/v1/discovery`
- `/api/v1/eta`
- `/api/v1/menu-items`
- `/api/v1/payments`
- `/api/v1/locations`
- `/api/v1/preferences`

## Verification

- Auth integration tests cover refresh rotation, logout revocation, and rate limiting.
- Payment integration tests cover minor units, mock webhook signature verification, and refunds.
- Role lifecycle tests cover v1 paginated endpoints.
- The backend suite covers unit, HTTP integration, Flyway, realtime, and payment regressions.
- The opt-in Testcontainers check applies every committed migration to MySQL 8.
