# Phase 3 — Payments

Turns the previously **mocked** payment step into a real, gateway-driven flow — without
requiring any payment credentials to run or test.

## Design

A `PaymentGateway` abstraction (mirrors the `RouteProvider` pattern):

- **`MockPaymentGateway`** (default, `ontheway.payment.provider=mock`): keyless, deterministic;
  approves any positive charge and returns a synthetic reference. Lets the whole order→pay
  flow be demoed and tested with no credentials.
- **Stripe** and **Razorpay** adapters are included behind the same interface and selected by
  configuration — no caller changes.

```
POST /api/payments  { orderId, paymentMethod }
  → service verifies the caller owns the order
  → service enforces idempotency (one payment per order)
  → gateway.charge(orderId, amount, method, idempotencyKey)
  → payment saved with status COMPLETED / FAILED (decided by the GATEWAY, not the client)
    plus the gateway name and reference
```

## Server-authoritative & safe

- The **gateway** decides the outcome; the client can never set payment status.
- **Amount** comes from the order total, not the request.
- **Idempotency**: an order has at most one payment; a second attempt returns `409 Conflict`
  (creation is serialized per order and an idempotency key is passed to the gateway).
- **Ownership**: only the order's owner may pay; others get `403`.
- **Monotonic state**: delayed webhooks cannot regress a completed or refunded payment.
- **Signed webhooks**: the route provider must match the configured gateway before its signature
  and payload are processed.

## Data model

`payments` gained (migration `V4__payment_gateway.sql`): `gateway`, `gateway_reference`.
The response now exposes `gateway` and `gatewayReference` alongside `paymentStatus`.

## Frontend

Checkout pays the order through the gateway immediately after placing it; the order page shows
a **payment badge** (e.g. `COMPLETED · via mock`).

## Tests

| Test | Proves |
|---|---|
| `PaymentServiceImplTest` | Gateway charge → COMPLETED; decline → FAILED; idempotency → 409; non-owner → 403. |
| `PaymentIntegrationTest` | End-to-end pay → COMPLETED via mock; second charge → 409; non-owner → 403. |

## Configuration

```
ontheway.payment.provider=mock        # mock | stripe | razorpay
ontheway.payment.stripe.apikey=...
ontheway.payment.stripe.webhook-secret=...
ontheway.payment.razorpay.apikey=...
ontheway.payment.razorpay.apisecret=...
```

Canonical minor-unit and currency fields are persisted alongside legacy decimal fields for API
compatibility. Refunds are admin-controlled and supported by each included gateway.
